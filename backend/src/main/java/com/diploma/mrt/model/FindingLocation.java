package com.diploma.mrt.model;

import java.util.List;

public record FindingLocation(
        String segment,
        List<Double> centroid,
        BoundingBox bbox,
        List<Integer> extent,
        String suspicion
) {
}
