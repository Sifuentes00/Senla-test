package com.matvey.senlatest.mapper;

import com.matvey.senlatest.dto.PricePageResponse;
import com.matvey.senlatest.dto.PriceRecordResponse;
import com.matvey.senlatest.model.PriceRecord;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceRecordMapper {

    public PriceRecordResponse toResponse(PriceRecord record) {
        return new PriceRecordResponse(record.getSymbol(), record.getPrice(), record.getFetchedAt());
    }

    public PricePageResponse toPageResponse(Page<PriceRecord> page) {
        List<PriceRecordResponse> items = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return new PricePageResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.hasNext()
        );
    }
}
