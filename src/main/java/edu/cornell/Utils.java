package edu.cornell;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Utils {
    public static int runSubprocess(List<String> command, File basedir, File output, long timeout, boolean append) {
        int exitCode;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.inheritIO();
            pb.directory(basedir);
            pb.redirectErrorStream(true);
            if (output != null) {
                if (append) {
                    pb.redirectOutput(ProcessBuilder.Redirect.appendTo(output));
                } else {
                    pb.redirectOutput(output);
                }
            }
            Process process = pb.start();
            if (timeout > 0) {
                exitCode = process.waitFor(timeout, TimeUnit.SECONDS) ? 0 : 1;
            } else {
                exitCode = process.waitFor();
            }
        } catch (IOException | InterruptedException ex) {
            ex.printStackTrace();
            exitCode = 1;
        }
        return exitCode;
    }
}
