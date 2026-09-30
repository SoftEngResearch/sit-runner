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

@Mojo(name = "restore", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class RestoreProjectMojo extends BaseMojo {

    @Override
    public void execute() throws MojoExecutionException {
        super.execute();
        getLog().info("Restoring block tests");

        File backupPath = Paths.get(outputDir.getAbsolutePath() + "-backup").toFile();

        List<Path> toReturn = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(backupPath.toPath())) {
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

        for (Path path : toReturn) {
            try {
                Path relativePath = backupPath.toPath().relativize(path);
                Path targetPath = Paths.get(project.getBasedir().getAbsolutePath()).resolve(relativePath);
                FileUtils.copyFile(path.toFile(), targetPath.toFile());
                getLog().info("Restored block test file: " + targetPath);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to restore block test file: " + path, e);
            }
        }
    }

}
