package com.flowgen.plugin;

import com.flowgen.scanner.SpringFlowGen;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;

@Mojo(name = "generate")
public class FlowGenMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.sourceDirectory}", readonly = true)
    private File sourceDirectory;

    @Parameter(property = "flowgen.outputDirectory", defaultValue = "docs/flowgen")
    private String outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        if (!sourceDirectory.exists()) {
            getLog().warn("Source directory not found: " + sourceDirectory);
            return;
        }

        try {
            SpringFlowGen.scan(sourceDirectory.getAbsolutePath())
                .print()
                .outputIndex(outputDirectory)
                .outputEach(outputDirectory);
        } catch (Exception e) {
            throw new MojoExecutionException("FlowGen failed", e);
        }
    }
}
