package edu.cornell;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

@Mojo(name = "compile", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class CompileMojo extends ParseMojo {
    private void compileSources(String srcDir, String binDir) throws IOException {
        String classpath = new File(depsFile).exists() ? new String(Files.readAllBytes(Paths.get(depsFile))) : "";
        String fullClasspath = classpath + File.pathSeparator + srcDir + File.pathSeparator
                + project.getBuild().getDirectory() + File.pathSeparator + "classes";
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                Arrays.asList(Objects.requireNonNull(new File(srcDir).listFiles(
                        (dir, name) -> name.endsWith(JAVA_SRC_EXTENSION)
                ))));
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null,
                Arrays.asList("-cp", fullClasspath, "-d", binDir), null, compilationUnits);
        if (!task.call()) {
            throw new IOException("Compilation failed");
        }
        fileManager.close();
    }

    @Override
    public void execute() throws MojoExecutionException {
        super.execute();
        try {
            if (!blockTestOnly) {
                compileSources(itestSrcDir, itestBinDir);
            }
            if (!inlineTestOnly) {
                compileSources(btestSrcDir, btestBinDir);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
