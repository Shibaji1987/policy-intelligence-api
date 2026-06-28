package com.shibajide.policyintelligence.ml.application;

public interface RetrievalQualityPredictor {

    RetrievalQualityPrediction predict(RetrievalQualityFeatures features);
}
