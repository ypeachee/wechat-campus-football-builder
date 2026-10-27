package com.campusfootball;

import org.apache.ibatis.annotations.Mapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;

import static org.assertj.core.api.Assertions.assertThat;

class MapperScanConfigurationTest {
    @Test
    void scansOnlyInterfacesExplicitlyMarkedAsMyBatisMappers() {
        MapperScan mapperScan = CampusFootballApplication.class.getAnnotation(MapperScan.class);

        assertThat(mapperScan).isNotNull();
        assertThat(mapperScan.basePackages()).containsExactly("com.campusfootball");
        assertThat(mapperScan.annotationClass()).isEqualTo(Mapper.class);
    }
}
