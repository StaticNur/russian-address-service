package com.staticnur.repositories.impl;

import com.staticnur.repositories.AddressDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AddressDaoImpl implements AddressDao {
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public AddressDaoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Map<String, Object>> findAllAddress(int page, int size) {
        int offset = page * size;
        String GET_ALL_QUERY = "SELECT o1.type_name as CityType, o1.name as City, o2.type_name as StreetType, " +
                               "o2.name as Street, ht.short_name as ShortNameHouse, h.house_num as House, " +
                               "rt.short_name as ShortNameRoom, r.number as Room " +
                               "FROM RUSSIAN_ADDRESS_DATA.adm_hierarchy i " +
                               "INNER JOIN RUSSIAN_ADDRESS_DATA.house h ON i.object_id = h.object_id " +
                               "INNER JOIN RUSSIAN_ADDRESS_DATA.addr_obj o1 ON CAST(SPLIT_PART(i.path, '.', 1) AS bigint) = o1.object_id " +
                               "INNER JOIN RUSSIAN_ADDRESS_DATA.addr_obj o2 ON i.parent_obj_id = o2.object_id " +
                               "INNER JOIN RUSSIAN_ADDRESS_DATA.house_type ht ON ht.id = h.house_type " +
                               "LEFT JOIN RUSSIAN_ADDRESS_DATA.room r ON CAST(SPLIT_PART(i.path, '.', 5) AS varchar) = r.object_id::varchar " +
                               "LEFT JOIN RUSSIAN_ADDRESS_DATA.room_type rt ON rt.id = r.room_type "
                               +"LIMIT ? OFFSET ?;";
        return jdbcTemplate.queryForList(GET_ALL_QUERY, size, offset);
    }

    @Override
    public void delete() {
        jdbcTemplate.update("DELETE FROM RUSSIAN_ADDRESS_DATA.addr_obj");
        jdbcTemplate.update("DELETE FROM RUSSIAN_ADDRESS_DATA.addr_obj_type");
        jdbcTemplate.update("DELETE FROM RUSSIAN_ADDRESS_DATA.adm_hierarchy");
        jdbcTemplate.update("DELETE FROM RUSSIAN_ADDRESS_DATA.apartment");
        jdbcTemplate.update("DELETE FROM RUSSIAN_ADDRESS_DATA.apartment_type");
        jdbcTemplate.update("DELETE FROM RUSSIAN_ADDRESS_DATA.house");
        jdbcTemplate.update("DELETE FROM RUSSIAN_ADDRESS_DATA.house_type");
        jdbcTemplate.update("DELETE FROM RUSSIAN_ADDRESS_DATA.room");
        jdbcTemplate.update("DELETE FROM RUSSIAN_ADDRESS_DATA.room_type");
    }

}
