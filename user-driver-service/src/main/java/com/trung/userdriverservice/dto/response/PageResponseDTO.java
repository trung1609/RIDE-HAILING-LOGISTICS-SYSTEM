package com.trung.userdriverservice.dto.response;

import lombok.*;

import java.util.List;


@AllArgsConstructor
@Builder
@Setter
@Getter
@NoArgsConstructor
public class PageResponseDTO <T>{
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
    private List<T> content;
    private Boolean hasNext;
    private Boolean hasPrevious;
}
