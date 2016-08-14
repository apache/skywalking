package com.a.eye.skywalking.reciever.peresistent;

public class FileRegisterEntry {
    private String                  fileName;
    private int                     offset;
    private FileRegisterEntryStatus status;

    public FileRegisterEntry() {
    }

    public FileRegisterEntry(String fileName) {
        this.fileName = fileName;
    }

    public FileRegisterEntry(String fileName, int offset) {
        this.fileName = fileName;
        this.offset = offset;
    }

    public FileRegisterEntry(String fileName, int offset, FileRegisterEntryStatus status) {
        this.fileName = fileName;
        this.offset = offset;
        this.status = status;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public FileRegisterEntryStatus getStatus() {
        return status;
    }

    public void setStatus(FileRegisterEntryStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof FileRegisterEntry))
            return false;

        FileRegisterEntry that = (FileRegisterEntry) o;

        return !(getFileName() != null ? !getFileName().equals(that.getFileName()) : that.getFileName() != null);

    }

    @Override
    public int hashCode() {
        return getFileName() != null ? getFileName().hashCode() : 0;
    }


    @Override
    public String toString() {
        return fileName + '\t' + offset + "\t" + status;
    }

    public enum FileRegisterEntryStatus {
        REGISTER,
        UNREGISTER;
    }
}
