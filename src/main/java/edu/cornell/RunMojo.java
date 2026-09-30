package edu.cornell;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "run", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class RunMojo extends CompileMojo {
    private String extractConsoleJar() throws IOException {
        InputStream in = getClass().getResourceAsStream(File.separator + JUNIT_JAR);
        if (in == null) {
            throw new FileNotFoundException("Failed to load JUnit Console Standalone JAR from resources.");
        }
        File junitJar = File.createTempFile("junit-console", ".jar");
        junitJar.deleteOnExit();
        Files.copy(in, junitJar.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return junitJar.getAbsolutePath();
    }

    private int runTests(String junitJar, String binDir, String name) {
        getLog().info("Running " + name);
        List<String> command = new ArrayList<>();
        List<String> classesStr = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(Paths.get(binDir))) {
            classesStr = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(JAVA_BIN_EXTENSION))
                    .map(p -> p.toString().substring(binDir.length() + 1,
                            p.toString().length() - JAVA_BIN_EXTENSION.length()).replace(File.separator, "."))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        try {
            command.addAll(Arrays.asList("java", "-jar", junitJar, "-cp",
                    new String(Files.readAllBytes(Paths.get(depsFile))).trim() + File.pathSeparator
                            + "target/classes" + File.pathSeparator
                            + binDir + File.pathSeparator));
            for (String testClass : classesStr) {
                command.add("--select-class=" + testClass);
            }
            return Utils.runSubprocess(command, project.getBasedir(), null, 0, false);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private List<String> findTestMethods(Path sourceFile) throws IOException {
        String source = new String(Files.readAllBytes(sourceFile));

        List<String> methods = new ArrayList<>();

        Pattern pattern = Pattern.compile("@Test\\s+(?:public\\s+)?void\\s+(\\w+)\\s*\\(");

        Matcher matcher = pattern.matcher(source);

        while (matcher.find()) {
            methods.add(matcher.group(1));
        }

        return methods;
    }

    private Path findSourceFile(String className) throws IOException {
        String simpleClassName;

        int index = className.lastIndexOf('.');
        if (index == -1) {
            simpleClassName = className;
        } else {
            simpleClassName = className.substring(index + 1);
        }

        String sourceFileName = simpleClassName + ".java";

        try (Stream<Path> stream = Files.walk(project.getBasedir().toPath())) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(sourceFileName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private int runBlockTests(String junitJar) {
        List<String> classesStr;

        try (Stream<Path> stream = Files.walk(Paths.get(btestBinDir))) {
            classesStr = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(JAVA_BIN_EXTENSION))
                    .map(p -> p.toString().substring(btestBinDir.length() + 1,
                            p.toString().length() - JAVA_BIN_EXTENSION.length()).replace(File.separator, "."))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        int result = 0;

        try {
            for (String testClass : classesStr) {
                Path sourceFile = findSourceFile(testClass);

                if (sourceFile == null) {
                    getLog().warn("Cannot find source file for " + testClass);
                    continue;
                }

                List<String> testMethods = findTestMethods(sourceFile);

                for (String testMethod : testMethods) {
                    String test = testClass + "#" + testMethod;

                    getLog().info("Running block test: " + test);

                    List<String> command = new ArrayList<>();
                    command.addAll(Arrays.asList("java", "-jar", junitJar, "-cp",
                            new String(Files.readAllBytes(Paths.get(depsFile))).trim() + File.pathSeparator
                                    + "target/classes" + File.pathSeparator
                                    + btestBinDir + File.pathSeparator, "--select-method=" + test));
                    result += Utils.runSubprocess(command, project.getBasedir(), null, 0, false);
                }
            }

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return result;
    }

    @Override
    public void execute() throws MojoExecutionException {
        super.execute();
        try {
            String junitJar = extractConsoleJar();
            if (!isolated) {
                int itestResult = blockTestOnly ? 0 : runTests(junitJar, itestBinDir, "inline tests");
                int btestResult = inlineTestOnly ? 0 : runTests(junitJar, btestBinDir, "block tests");
                if (itestResult + btestResult != 0) {
                    throw new MojoExecutionException("Some tests failed. Please check the console output for details.");
                }
            } else {
                int testResult = runBlockTests(junitJar);
                if (testResult != 0) {
                    throw new MojoExecutionException("Some block tests failed. Please check the console output for details.");
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
