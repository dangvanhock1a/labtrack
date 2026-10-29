package com.pharmalab.labtrack.service;

/**
 * Một file để tải về (BR-16): tên file và nội dung đã mã hóa. Tầng nghiệp vụ dựng cả hai, vì tên file theo ngày giờ VN
 * cần {@link java.time.Clock}, và BOM cùng mã hóa UTF-8 là một phần của quy tắc BR-16 chứ không phải việc của web.
 */
public record CsvFile(String fileName, byte[] content) {

}
