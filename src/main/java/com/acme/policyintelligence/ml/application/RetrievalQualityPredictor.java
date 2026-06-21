package com.acme.policyintelligence.ml.application;

public interface RetrievalQualityPredictor {

    RetrievalQualityPrediction predict(RetrievalQualityFeatures features);
}
