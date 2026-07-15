package com.enterprise.bulkimport.dto;

import com.enterprise.bulkimport.enums.RecordStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRecordResponse {
    private String id;
    private Integer rowNumber;
    private String data;
    private RecordStatus status;
    private String errorMessage;
}
