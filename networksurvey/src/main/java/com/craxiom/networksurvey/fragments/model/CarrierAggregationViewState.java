package com.craxiom.networksurvey.fragments.model;

import java.util.List;

/**
 * View state for the carrier aggregation section on the cellular details screen: one display label
 * per component carrier chip plus the summary line above them. Kept as a record over immutable
 * display strings so the ViewModel's distinct-until-changed setter works via value equality, which
 * bounds the chip re-inflation to actual changes (a raw int[] would compare by reference and
 * re-bind on every scan).
 *
 * @param chipLabels The per-carrier bandwidth chip labels (e.g. "20 MHz"), in ServiceState order.
 * @param summary    The summary line (e.g. "Carrier Aggregation · 5 carriers · 235 MHz").
 */
public record CarrierAggregationViewState(List<String> chipLabels, String summary)
{
}
