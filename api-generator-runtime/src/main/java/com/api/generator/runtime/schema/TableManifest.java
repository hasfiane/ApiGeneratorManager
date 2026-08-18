package com.api.generator.runtime.schema;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.EnumSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class TableManifest {
    private Set<Operation> operations = EnumSet.allOf(Operation.class);
    public boolean allows(Operation op) { return operations == null || operations.contains(op); }
}
