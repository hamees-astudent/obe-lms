package com.lms.modules.courses;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialCloMappingId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "material_id")
    private UUID materialId;

    @Column(name = "clo_id")
    private UUID cloId;
}
