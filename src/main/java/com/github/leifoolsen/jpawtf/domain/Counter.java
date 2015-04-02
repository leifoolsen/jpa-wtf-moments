package com.github.leifoolsen.jpawtf.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Version;
import java.io.Serializable;

@Entity
public class Counter implements Serializable {

    private static final long serialVersionUID = -531184025040843883L;

    @Id
    @Column(length=36)
    public String id;

    @Version
    public Long version;

    public int count;
    public int maxCount;

    public Counter() {}

    public Counter(String id, int count, int maxCount) {
        this.id = id;
        this.count = count;
        this.maxCount = maxCount;
    }
}
