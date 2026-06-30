package com.km.search.client;

import com.km.search.dto.AiRetrievalRequest;
import com.km.search.dto.AiRetrievalResponse;

public interface AiRetrievalClient {

    AiRetrievalResponse search(AiRetrievalRequest request);
}

