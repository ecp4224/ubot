package me.eddiep.ubot.utils;

public enum UpdateType {
    NONE,
    BUGFIX,
    MINOR,
    MAJOR,
    URGENT;

    public static UpdateType getUpdateType(String oldVersion, String newVersion) {
        String[] oldVersionNums = oldVersion.split("\\.");
        String[] newVersionNums = newVersion.split("\\.");
        try {
            int oMajor = Integer.parseInt(oldVersionNums[0]);
            int oMinor = Integer.parseInt(oldVersionNums[1]);
            int oBugfix = Integer.parseInt(oldVersionNums[2].split("\\+")[0]);
            int oUrgent = Integer.parseInt(oldVersionNums[2].split("\\+")[1]);

            int nMajor = Integer.parseInt(newVersionNums[0]);
            int nMinor = Integer.parseInt(newVersionNums[1]);
            int nBugfix = Integer.parseInt(newVersionNums[2].split("\\+")[0]);
            int nUrgent = Integer.parseInt(newVersionNums[2].split("\\+")[1]);

            if (nUrgent > oUrgent) { //This is an urgent update, regardless of other versions
                return UpdateType.URGENT;
            }

            if (nMajor > oMajor) {
                return UpdateType.MAJOR;
            }

            if (nMinor > oMinor) {
                return UpdateType.MINOR;
            }

            if (nBugfix > oBugfix) {
                return UpdateType.BUGFIX;
            }

            return UpdateType.NONE;
        } catch (Throwable t) {
            return UpdateType.NONE;
        }
    }
}
