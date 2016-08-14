package com.a.eye.skywalking.reciever.peresistent;

import com.a.eye.skywalking.protocol.SerializedFactory;
import com.a.eye.skywalking.protocol.common.AbstractDataSerializable;
import com.a.eye.skywalking.protocol.exception.ConvertFailedException;
import com.a.eye.skywalking.protocol.util.IntegerAssist;
import com.a.eye.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.a.eye.skywalking.reciever.selfexamination.ServerHeathReading;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BufferFileReader {
    private File            bufferFile;
    private FileInputStream bufferInputStream;
    private int             currentOffset;
    private static final byte[] DATA_SPILT      = new byte[] {127, 127, 127, 127};
    private              int    remainderLength = 0;
    private              byte[] remainderByte   = null;
    private              Logger logger          = LogManager.getLogger(BufferFileReader.class);
    private List<AbstractDataSerializable> serializables;

    public BufferFileReader(File bufferFile, int currentOffset) {
        this.bufferFile = bufferFile;
        this.currentOffset = currentOffset;
        try {
            this.bufferInputStream = new FileInputStream(bufferFile);
            bufferInputStream.skip(currentOffset);
        } catch (IOException e) {
        }

    }

    /**
     * <p>
     * 按照如下格式读取:
     * 1.头4位长度
     * 2.根据长度读取正文
     * 2.1. 正文包含多个ISerializable。每个块包含每个ISerializable的长度和ISerializable的正文
     * 3.长度外,读取4位,为分隔符(不可见字符)
     * <p>
     * 封装方法要求:
     * 1.封装读取指定长度块的方法,读取完成则返回。读取长度不足,则缓存并等待。
     * <p>
     * 异常处理:
     * 1.长度外,读取4位,不是分隔符: 则启动异常跳位处理,直到读取到分隔符位置
     *
     * @return
     */
    public boolean hasNext() {
        try {
            int length = unpackLength();
            byte[] dataContext = readByte(length);
            // 转换对象
            serializables = deserializableObjects(dataContext);

            byte[] skip = new byte[4];
            bufferInputStream.read(skip);
            if (!Arrays.equals(DATA_SPILT, skip)) {
                skipToNext();
            }
            MemoryRegister.instance().updateOffSet(bufferFile.getName(), currentOffset);
        } catch (IOException e) {
            logger.error("The data file I/O exception.", e);
            ServerHealthCollector.getCurrentHeathReading(null).updateData(ServerHeathReading.ERROR, e.getMessage());
            return false;
        }

        return serializables.get(0).getDataType() != -1;
    }

    private byte[] readByte(int length) throws IOException {
        byte[] dataContext = new byte[length];
        int realLength = bufferInputStream.read(dataContext);
        currentOffset += realLength;
        int remainderLength = length - (realLength == -1 ? 0 : realLength);
        while (remainderLength > 0) {
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                logger.error("Failed to sleep.", e);
            }

            byte[] remainderByte = new byte[remainderLength];
            int readLength = bufferInputStream.read(remainderByte, 0, remainderLength);
            if (readLength == -1) {
                continue;
            }

            currentOffset += realLength;
            System.arraycopy(remainderByte, 0, dataContext, length - remainderLength, readLength);
            remainderLength -= readLength;
        }
        return dataContext;
    }

    public List<AbstractDataSerializable> next() {
        return serializables;
    }

    private int unpackLength() throws IOException {
        byte[] lengthByte = readByte(4 - remainderLength);
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

    public void skipToNext() throws IOException {
        byte[] previousDataByte = new byte[4];
        byte[] currentDataByte = new byte[4];
        byte[] compactDataByte = new byte[8];
        while (true) {
            currentDataByte = readByte(4);

            if (Arrays.equals(currentDataByte, DATA_SPILT)) {
                remainderLength = 0;
                break;
            }

            System.arraycopy(previousDataByte, 0, compactDataByte, 0, 4);
            System.arraycopy(currentDataByte, 0, compactDataByte, 4, 4);

            int index = bytesIndexOf(compactDataByte, DATA_SPILT, 0, 8);
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


    public static List<AbstractDataSerializable> deserializableObjects(byte[] dataPackage) {
        List<AbstractDataSerializable> serializeData = new ArrayList<AbstractDataSerializable>();
        int currentLength = 0;
        while (true) {
            //读取长度
            int dataLength = IntegerAssist.bytesToInt(dataPackage, currentLength);
            // 反序列化
            byte[] data = new byte[dataLength];
            System.arraycopy(dataPackage, currentLength + 4, data, 0, dataLength);

            try {
                AbstractDataSerializable abstractDataSerializable = SerializedFactory.deserialize(data);
                serializeData.add(abstractDataSerializable);
            } catch (ConvertFailedException e) {
                // FIXME: 16/8/4 logger日志输出
                e.printStackTrace();
            }

            currentLength += 4 + dataLength;
            if (currentLength >= dataPackage.length) {
                break;
            }
        }

        return serializeData;
    }
}
