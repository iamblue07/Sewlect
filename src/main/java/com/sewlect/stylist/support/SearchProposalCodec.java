package com.sewlect.stylist.support;

import com.sewlect.stylist.domain.SearchProposal;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@AllArgsConstructor
public class SearchProposalCodec {

    private final JsonMapper jsonMapper;

    public String encode(SearchProposal proposal) {
        return jsonMapper.writeValueAsString(proposal);
    }

    public SearchProposal decode(String json) {
        return jsonMapper.readValue(json, SearchProposal.class);
    }
}
