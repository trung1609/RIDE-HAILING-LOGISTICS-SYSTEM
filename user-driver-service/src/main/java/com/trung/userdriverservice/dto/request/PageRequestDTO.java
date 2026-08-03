package com.trung.userdriverservice.dto.request;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PageRequestDTO {
    private Integer page;
    private Integer size;
    private String sortBy;
    private String sortDirection;
}
