package com.gims.module.dbsync.initializer;

import com.gims.module.dbsync.entity.source.SourceData;
import com.gims.module.dbsync.repository.source.SourceDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Source DB 샘플 데이터 초기화
 *
 * Source DB가 비어있으면 자동으로 샘플 데이터를 생성합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleDataInitializer implements ApplicationRunner {

    private final SourceDataRepository sourceDataRepository;

    @Override
    @Transactional("sourceTransactionManager")
    public void run(ApplicationArguments args) throws Exception {
        long count = sourceDataRepository.count();

        if (count == 0) {
            log.info("Source DB가 비어있습니다. 샘플 데이터를 생성합니다...");
            initializeSampleData();
            log.info("샘플 데이터 생성 완료: 1000건");
        } else {
            log.info("Source DB에 기존 데이터가 있습니다: {}건", count);
        }
    }

    private void initializeSampleData() {
        List<SourceData> sampleData = new ArrayList<>();

        for (int i = 1; i <= 1000; i++) {
            SourceData data = SourceData.builder()
                    .name("Sample_Data_" + i)
                    .value1(Math.random() * 100)
                    .value2(Math.random() * 200)
                    .value3(Math.random() * 300)
                    .build();
            sampleData.add(data);

            // 배치 사이즈마다 저장
            if (i % 100 == 0) {
                sourceDataRepository.saveAll(sampleData);
                sampleData.clear();
                log.debug("샘플 데이터 {}건 저장 완료", i);
            }
        }

        // 나머지 데이터 저장
        if (!sampleData.isEmpty()) {
            sourceDataRepository.saveAll(sampleData);
        }
    }
}
