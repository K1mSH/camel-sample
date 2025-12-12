package com.gims.module.dbsync.repository.source;

import com.gims.module.dbsync.entity.source.SourceData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Source DB Repository
 */
@Repository
public interface SourceDataRepository extends JpaRepository<SourceData, Long> {
}
