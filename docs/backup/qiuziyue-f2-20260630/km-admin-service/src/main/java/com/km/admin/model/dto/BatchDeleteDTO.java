package com.km.admin.model.dto;

import javax.validation.constraints.NotEmpty;
import java.util.List;

public class BatchDeleteDTO {

    @NotEmpty(message = "ids ????")
    private List<Long> ids;

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}
