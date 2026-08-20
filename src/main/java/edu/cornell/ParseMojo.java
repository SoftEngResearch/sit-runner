package edu.cornell;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.inlinetest.InlineTestRunnerSourceCode;
import org.blocktest.BlockTestRunnerSourceCode;

@Mojo(name = "parse", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class ParseMojo extends BaseMojo {
    /**
     * Search for source files that contain the search content
     * (e.g., "org.inlinetest.ITest") in the compile source roots.
     * @param searchContent the content to search for each source file
     * @return a list of source file paths that contain the search content
     */
    private List<Path> getSourcePathList(String searchContent) {
        List<Path> toReturn = new ArrayList<>();
        List<String> compileSourceRoots = project.getCompileSourceRoots();
        for (String sourceRoot : compileSourceRoots) {
            try (Stream<Path> stream = Files.walk(Paths.get(sourceRoot))) {
                toReturn = stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(JAVA_SRC_EXTENSION))
                        .filter(p -> {
                            try {
                                return Files.readAllLines(p).toString().contains(searchContent);
                            } catch (IOException ex) {
                                return false;
                            }
                        })
                        .collect(Collectors.toList());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return toReturn;
    }

    @Override
    public void execute() throws MojoExecutionException {
        super.execute();
        List<String> compileSourceRoots = project.getCompileSourceRoots();
        if (!blockTestOnly) {
            for (Path inputFilePath : getSourcePathList(ITEST_CLASS)) {
                InlineTestRunnerSourceCode.main(new String[] {
                        "--input_file=" + inputFilePath,
                        "--assertion_style=junit",
                        "--output_dir=" + itestSrcDir,
                        "--multiple_test_classes=true",
                        "--dep_file_path=" + depsFile,
                        "--app_src_path=" + compileSourceRoots.get(0) // TODO: Support multiple source roots
                });
            }
        }
        if (!inlineTestOnly) {
            for (Path inputFilePath : getSourcePathList(BTEST_CLASS)) {
                PrintStream originalOut = System.out;
                PrintStream originalErr = System.err;
                String[] command = new String[] {
                    "--input_file=" + inputFilePath,
                    "--assertion_style=junit",
                    "--junit_version=junit5",
                    "--output_dir=" + btestSrcDir,
                    "--multiple_test_classes=true",
                    "--dep_file_path=" + depsFile,
                    "--app_src_path=" + compileSourceRoots.get(0) // TODO: Support multiple source roots
                };
                try {
                    PrintStream silent = new PrintStream(new OutputStream() {
                        @Override
                        public void write(int b) {
                        }
                    });
                    System.setOut(silent);
                    System.setErr(silent);
                    BlockTestRunnerSourceCode.main(command);
                } finally {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                    System.err.println("command: " + String.join(" ", command));
                }
            }
        }
    }
}
