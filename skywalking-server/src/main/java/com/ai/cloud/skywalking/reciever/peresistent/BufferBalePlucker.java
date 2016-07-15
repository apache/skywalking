package com.ai.cloud.skywalking.reciever.peresistent;

import com.ai.cloud.skywalking.protocol.util.IntegerAssist;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

public class BufferBalePlucker {
    private File            bufferFile;
    private FileInputStream bufferInputStream;
    private int             currentOffset;
    private              boolean       hasNextBufferBale = false;
    private static final byte[]        SPILT_BALE_ARRAY  = new byte[] {127, 127, 127, 127};
    private static final byte[]        EOF_BALE_ARRAY    = "EOF".getBytes();
    private              int           remainderLength   = 0;
    private              byte[]        remainderByte     = null;
    private              Logger        logger            = LogManager.getLogger(BufferBalePlucker.class);
    private              PluckerStatus status            = PluckerStatus.INITIAL;

    public BufferBalePlucker(File bufferFile, int currentOffset) {
        this.bufferFile = bufferFile;
        this.currentOffset = currentOffset;

        if (bufferFile.length() >= currentOffset) {
            hasNextBufferBale = true;
        }

        try {
            this.bufferInputStream = new FileInputStream(bufferFile);
            bufferInputStream.skip(currentOffset);
        } catch (IOException e) {
            hasNextBufferBale = false;
        }

    }

    public boolean hasNextBufferBale() {

        if (status == PluckerStatus.SUSPEND) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                logger.error("Failed to sleep ", e);
            }
        }

        if (status == PluckerStatus.INITIAL) {
            status = PluckerStatus.RUNNING;
            return hasNextBufferBale;
        }

        if (status == PluckerStatus.RUNNING) {
            byte[] spiltArray = new byte[4];
            try {
                bufferInputStream.read(spiltArray);
                if (!Arrays.equals(spiltArray, SPILT_BALE_ARRAY)) {
                    skipToNextBufferBale();
                }
            } catch (IOException e) {
                return false;
            }
            currentOffset += 4;
        }
        MemoryRegister.instance().updateOffSet(bufferFile.getName(), currentOffset);
        return hasNextBufferBale;
    }

    public byte[] pluck() throws IOException {
        int packageLength = unpackBaleLength();
        byte[] dataPackage = unpackDataContext(packageLength);

        if (dataPackage == null || dataPackage.length == 0) {
            // 文件没有完结,但是已经被处理完成了
            return null;
        }

        if (checkDataPackageIsEOF(dataPackage)) {
            hasNextBufferBale = false;
            return null;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Pluck bale size : " + dataPackage.length);
        }

        return dataPackage;
    }

    private boolean checkDataPackageIsEOF(byte[] dataPackage) {
        if (dataPackage.length == EOF_BALE_ARRAY.length) {
            return Arrays.equals(dataPackage, EOF_BALE_ARRAY);
        }
        return false;
    }

    private byte[] unpackDataContext(int length) throws IOException {
        if (currentOffset >= bufferFile.length()) {
            status = PluckerStatus.SUSPEND;
            return null;
        }
        byte[] dataContext = new byte[length];
        bufferInputStream.read(dataContext);
        currentOffset += length;
        return dataContext;
    }

    private int unpackBaleLength() {
        int length;

        while (true) {
            try {
                length = calculateCurrentPackageLength();
                if (length > 0 && length < 90000) {
                    break;
                }
                skipToNextBufferBale();
            } catch (IOException e) {
                skipToNextBufferBale();
            }
        }

        return length;
    }

    private int calculateCurrentPackageLength() throws IOException {
        byte[] lengthByte = new byte[4 - remainderLength];
        bufferInputStream.read(lengthByte);
        currentOffset += 4 - remainderLength;
        lengthByte = spliceRemainderByteOfPreviousSkipIfNecessary(lengthByte);
        return IntegerAssist.bytesToInt(lengthByte, 0);
    }

    private byte[] spliceRemainderByteOfPreviousSkipIfNecessary(byte[] lengthByte) {
        if (remainderLength != 0) {
            byte[] length = Arrays.copyOf(remainderByte, 4);
            System.arraycopy(lengthByte, 0, length, remainderLength, lengthByte.length);
            remainderLength = 0;
            return length;
        }
        return lengthByte;
    }

    public void skipToNextBufferBale() {
        byte[] previousDataByte = new byte[4];
        byte[] currentDataByte = new byte[4];
        byte[] compactDataByte = new byte[8];
        while (true) {
            try {
                currentOffset += bufferInputStream.read(currentDataByte);
            } catch (IOException e) {
                hasNextBufferBale = false;
            }

            if (Arrays.equals(currentDataByte, SPILT_BALE_ARRAY)) {
                remainderLength = 0;
                break;
            }

            //
            if (currentOffset + 8000 >= bufferFile.length()) {
                status = PluckerStatus.SUSPEND;
                break;
            }

            System.arraycopy(previousDataByte, 0, compactDataByte, 0, 4);
            System.arraycopy(currentDataByte, 0, compactDataByte, 4, 4);

            int index = bytesIndexOf(compactDataByte, SPILT_BALE_ARRAY, 0, 8);
            if (index != -1) {
                recodeRemainderByteAndLength(compactDataByte, index);
                break;
            }

            previousDataByte = Arrays.copyOf(currentDataByte, 4);
        }
    }

    private void recodeRemainderByteAndLength(byte[] compactDataByte, int index) {
        remainderLength = 8 - 4 - index;
        remainderByte = Arrays.copyOfRange(compactDataByte, index + 4, index + 4 + remainderLength);
    }

    private int bytesIndexOf(byte[] Source, byte[] Search, int fromIndex, int endIndex) {
        boolean find = false;
        int i;
        for (i = fromIndex; i < endIndex - Search.length; i++) {
            if (Source[i] == Search[0]) {
                find = true;
                for (int j = 0; j < Search.length; j++) {
                    if (Source[i + j] != Search[j]) {
                        find = false;
                    }
                }
            }
            if (find) {
                break;
            }
        }
        return !find ? -1 : i;
    }

    public void close() throws IOException {
        if (bufferInputStream != null) {
            bufferInputStream.close();
        }
        logger.info("Data in file[{}] has been successfully processed", bufferFile.getName());
        boolean deleteSuccess = false;
        while (!deleteSuccess) {
            deleteSuccess = FileUtils.deleteQuietly(new File(bufferFile.getParent(), bufferFile.getName()));
        }
        logger.info("Delete file[{}] {}", bufferFile.getName(), (deleteSuccess ? "success" : "failed"));
        MemoryRegister.instance().removeEntry(bufferFile.getName());
        bufferFile = null;
    }

    enum PluckerStatus {
        INITIAL,
        RUNNING,
        SUSPEND
    }

}
