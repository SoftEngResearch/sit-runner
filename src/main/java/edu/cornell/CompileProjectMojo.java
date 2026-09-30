package edu.cornell;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.blocktest.utils.Util;

@Mojo(name = "compileProject", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class CompileProjectMojo extends BaseMojo {

    @Parameter(property = "compileTest", defaultValue = "true")
    protected boolean compileTest;

    @Parameter(property = "autoRestore", defaultValue = "true")
    protected boolean autoRestore;

    @Parameter(property = "autoCompile", defaultValue = "true")
    protected boolean autoCompile;

    @Override
    public void execute() throws MojoExecutionException {
        super.execute();
        getLog().info("Compiling project without block tests");

        File backupPath = Paths.get(outputDir.getAbsolutePath() + "-backup").toFile();
        try {
            if (backupPath.exists()) {
                FileUtils.deleteDirectory(backupPath);
            }
            FileUtils.forceMkdir(backupPath);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to create backup directory", e);
        }

        // Search files that contain block tests
        List<Path> blockTestFiles = searchBlockTests();
        for (Path path : blockTestFiles) {
            Path relativePath = project.getBasedir().toPath().relativize(path);
            File backupFile = backupPath.toPath().resolve(relativePath).toFile();

            try {
                FileUtils.copyFile(path.toFile(), backupFile);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to backup file: " + path, e);
            }

            Util.removeBlockTests(path.toString());
        }

        if (!autoCompile) {
            getLog().info("Block tests are removed. Please compile the project manually then run [mvn sit-runner:restore] to restore the block tests.");
            return;
        }

        compile();

        if (!autoRestore) {
            getLog().info("Block tests are removed and project is compiled. Please run [mvn sit-runner:restore] to restore the block tests.");
            return;
        }

        for (Path path : blockTestFiles) {
            Path relativePath = project.getBasedir().toPath().relativize(path);
            File backupFile = backupPath.toPath().resolve(relativePath).toFile();

            try {
                FileUtils.copyFile(backupFile, path.toFile());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to restore file: " + path, e);
            }
        }
    }

    private void compile() throws MojoExecutionException {
        // Run Maven compile/test-compile phase
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(project.getFile());
        request.addArg(compileTest ? "test-compile" : "compile");

        Invoker invoker = new DefaultInvoker();

        try {
            InvocationResult result = invoker.execute(request);

            if (result.getExitCode() != 0) {
                throw new MojoExecutionException(
                        "Failed to compile project, Maven exited with code " + result.getExitCode());
            }
        } catch (MavenInvocationException e) {
            throw new MojoExecutionException("Failed to invoke Maven", e);
        }
    }

    private List<Path> searchBlockTests() {
        List<Path> toReturn = new ArrayList<>();
        List<String> compileSourceRoots = project.getCompileSourceRoots();
        for (String sourceRoot : compileSourceRoots) {
            try (Stream<Path> stream = Files.walk(Paths.get(sourceRoot))) {
                toReturn = stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(JAVA_SRC_EXTENSION))
                        .filter(p -> {
                            try {
                                return Files.readAllLines(p).toString().contains("blocktest(") || Files.readAllLines(p).toString().contains("lambdatest(");
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
}
