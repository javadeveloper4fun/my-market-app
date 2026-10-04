package ru.yandex.practicum.mymarket.dto;

public record Paging(int pageSize, int pageNumber, boolean hasPrevious, boolean hasNext) {
    public static Paging from(org.springframework.data.domain.Page<?> page) {
        int pageNumber = page.getNumber() + 1; // 1-based for templates
        int pageSize = page.getSize();
        return new Paging(pageSize, pageNumber, pageNumber > 1, page.hasNext());
    }
}
