package com.cuenti.app.api.dto;

import com.cuenti.app.model.Category;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDTO {
    private Long id;
    private String name;
    private String fullName;
    private Category.CategoryType type;
    private Long parentId;
    private String parentName;
}
