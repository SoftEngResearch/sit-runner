package edu.cornell;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.commons.io.FileUtils;
import org.apache.maven.project.MavenProject;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@Mojo(name = "base", defaultPhase = LifecyclePhase.TEST, requiresDependencyResolution = ResolutionScope.TEST)
public class BaseMojo extends AbstractMojo implements Constants {
    @Parameter(defaultValue = "${project}", readonly = true, required = false)
    protected MavenProject project;

    @Parameter(property = "outputDir", defaultValue = ".sit-runner", required = false)
    protected File outputDir;

    @Parameter(property = "itestDir", defaultValue = "inlinetests")
    protected String itestDir;

    @Parameter(property = "btestDir", defaultValue = "blocktests")
    protected String btestDir;

    @Parameter(property = "inlineTestOnly", defaultValue = "false")
    protected boolean inlineTestOnly;

    @Parameter(property = "blockTestOnly", defaultValue = "false")
    protected boolean blockTestOnly;

    @Parameter(property = "isolated", defaultValue = "false")
    protected boolean isolated;

    protected String depsFile;
    protected String itestSrcDir;
    protected String itestBinDir;
    protected String btestSrcDir;
    protected String btestBinDir;

    private void removeOutput() {
        try {
            if (outputDir.exists()) {
                FileUtils.deleteDirectory(outputDir);
            }
            FileUtils.forceMkdir(outputDir);
            if (!blockTestOnly) {
                itestSrcDir = outputDir.getAbsolutePath() + File.separator + itestDir + File.separator + SRC;
                itestBinDir = outputDir.getAbsolutePath() + File.separator + itestDir + File.separator + BIN;
                FileUtils.forceMkdir(new File(itestSrcDir));
                FileUtils.forceMkdir(new File(itestBinDir));
            }
            if (!inlineTestOnly) {
                btestSrcDir = outputDir.getAbsolutePath() + File.separator + btestDir + File.separator + SRC;
                btestBinDir = outputDir.getAbsolutePath() + File.separator + btestDir + File.separator + BIN;
                FileUtils.forceMkdir(new File(btestSrcDir));
                FileUtils.forceMkdir(new File(btestBinDir));
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void writeDepsFile() {
        depsFile = outputDir.getAbsolutePath() + File.separator + DEPS_FILE_NAME;
        try {
            // Make sure that dependency resolution scope is correct, don't do it too early.
            List<String> elements = project.getRuntimeClasspathElements();
            String classpath = String.join(File.pathSeparator, elements);
            try (FileWriter writer = new FileWriter(depsFile)) {
                writer.write(classpath);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void execute() throws MojoExecutionException {
        removeOutput();
        writeDepsFile();
    }
}
