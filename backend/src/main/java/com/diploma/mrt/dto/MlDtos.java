package com.diploma.mrt.dto;

public class MlDtos {
    public record MlHealthResponse(String status, String mode, String version, Boolean liverModelConfigured, Boolean lesionModelConfigured, Boolean mriHeuristicSupported) {}
    public record MlCapabilitiesResponse(
            String schemaVersion,
            String service,
            String version,
            java.util.List<String> modes,
            java.util.List<String> modalities,
            java.util.List<String> inputFormats,
            java.util.List<String> outputs,
            Boolean supports3dLiver,
            Boolean supports3dSuspiciousZoneCt,
            Boolean supports3dSuspiciousZoneMri
    ) {}
}
