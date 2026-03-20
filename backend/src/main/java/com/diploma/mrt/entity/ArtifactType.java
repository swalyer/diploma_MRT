package com.diploma.mrt.entity;

public enum ArtifactType {
    ORIGINAL_STUDY(true, false),
    ENHANCED(false, true),
    ENHANCED_VOLUME(false, true),
    LIVER_MASK(false, true),
    LESION_MASK(false, true),
    LIVER_MESH(false, true),
    LESION_MESH(false, true);

    private final boolean sourceStudy;
    private final boolean generatedOutput;

    ArtifactType(boolean sourceStudy, boolean generatedOutput) {
        this.sourceStudy = sourceStudy;
        this.generatedOutput = generatedOutput;
    }

    public boolean isSourceStudy() {
        return sourceStudy;
    }

    public boolean isGeneratedOutput() {
        return generatedOutput;
    }

    public static ArtifactType canonicalSourceStudy() {
        return ORIGINAL_STUDY;
    }
}
