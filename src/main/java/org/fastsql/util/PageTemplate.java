package org.fastsql.util;

import org.fastsql.config.DatabaseType;
import org.fastsql.dto.ResultPage;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

/**
 * @author 陈佳志
 * 2017-08-15
 */
public class PageTemplate {

    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public PageTemplate(NamedParameterJdbcTemplate template) {
        this.namedParameterJdbcTemplate = template;
    }

//    public <T> ResultPage<T> queryPage(String sql, int page, int perPage, SqlParameterSource paramSource, RowMapper<T> rowMapper) {
//        String rowsSQL = PageUtils.getRowsSQL(sql, page, perPage);
//        List<T> list = namedParameterJdbcTemplate.query(
//                rowsSQL,
//                paramSource,
//                rowMapper);
//
//        //查询数量
//        String numberSQL = PageUtils.getNumberSQL(sql);
//        Integer number = namedParameterJdbcTemplate.queryForObject(
//                numberSQL,
//                paramSource,
//                Integer.class);
//        return new ResultPage<T>(list, number);
//    }
//
    public <T> ResultPage<T> queryPage(String sql, int page, int perPage, SqlParameterSource paramSource, RowMapper<T> rowMapper, DatabaseType databaseType) {
        String rowsSQL = PageUtils.getRowsSQL(sql, page, perPage, databaseType);
        List<T> list = namedParameterJdbcTemplate.query(
                rowsSQL,
                paramSource,
                rowMapper);

        //查询数量
        String numberSQL = PageUtils.getNumberSQL(sql);
        Integer number = namedParameterJdbcTemplate.queryForObject(
                numberSQL,
                paramSource,
                Integer.class);
        return new ResultPage<T>(list, number);
    }

//    public <T> ResultPage<T> queryPage(String sql, int page, int perPage, Object[] objects, RowMapper<T> rowMapper) {
//        String rowsSQL = PageUtils.getRowsSQL(sql, page, perPage);
//        List<T> list = namedParameterJdbcTemplate.getJdbcOperations().query(
//                rowsSQL,
//                objects,
//                rowMapper);
//
//        //查询数量
//        String numberSQL = PageUtils.getNumberSQL(sql);
//        Integer number = namedParameterJdbcTemplate.getJdbcOperations().queryForObject(
//                numberSQL,
//                objects,
//                Integer.class);
//        return new ResultPage<T>(list, number);
//    }

    public <T> ResultPage<T> queryPage(String sql, int page, int perPage, Object[] objects, RowMapper<T> rowMapper, DatabaseType databaseType) {
        String rowsSQL = PageUtils.getRowsSQL(sql, page, perPage, databaseType);
        List<T> list = namedParameterJdbcTemplate.getJdbcOperations().query(
                rowsSQL,
                objects,
                rowMapper);

        //查询数量
        String numberSQL = PageUtils.getNumberSQL(sql);
        Integer number = namedParameterJdbcTemplate.getJdbcOperations().queryForObject(
                numberSQL,
                objects,
                Integer.class);
        return new ResultPage<T>(list, number);
    }
}
