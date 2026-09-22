package com.sabyeguyz.clinic.databasev2.patient;

import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
@Profile("database-v2")
public class DatabaseV2PatientRepository {

    public record PatientRecord(
            Long id,
            String hn,
            String customerType,
            String title,
            String firstName,
            String lastName,
            String nickname,
            String gender,
            String genderDetail,
            String phone,
            Long branchId,
            String branchCode,
            String branchName,
            String nationalId,
            String passportNo,
            LocalDate birthdate,
            String bloodGroup,
            String nationality,
            String address,
            LocalDateTime registeredAt,
            Long registeredBy,
            Long customerGroupValueId,
            String customerGroupName,
            Long referralChannelValueId,
            String referralChannelName,
            Long insuranceCompanyValueId,
            String insuranceCompanyName
    ) {
        public String fullName() {
            if (lastName == null || lastName.isBlank() || "-".equals(lastName)) {
                return firstName;
            }
            return firstName + " " + lastName;
        }
    }

    private final JdbcTemplate jdbc;

    private static final String SELECT_PATIENT = """
            select p.patient_id, p.hn, cast(p.customer_type as varchar) as customer_type,
                   cast(p.title as varchar) as title, p.patient_fname, p.patient_lname,
                   p.patient_nickname, cast(p.gender as varchar) as gender, p.gender_detail,
                   p.phone, p.branch_id, b.branch_code, b.branch_name,
                   p.national_id, p.passport_no, p.birthdate, cast(p.blood_group as varchar) as blood_group,
                   p.nationality, p.address, p.registered_at, p.registered_by,
                   p.customer_group_value_id, cg.value_name as customer_group_name,
                   p.referral_channel_value_id, rc.value_name as referral_channel_name,
                   p.insurance_company_value_id, ic.value_name as insurance_company_name
            from patient p
            join branch b on b.branch_id = p.branch_id
            left join master_data_value cg on cg.value_id = p.customer_group_value_id
            left join master_data_value rc on rc.value_id = p.referral_channel_value_id
            left join master_data_value ic on ic.value_id = p.insurance_company_value_id
            """;

    private static final RowMapper<PatientRecord> ROW_MAPPER = (rs, rowNum) -> new PatientRecord(
            rs.getLong("patient_id"),
            rs.getString("hn"),
            rs.getString("customer_type"),
            rs.getString("title"),
            rs.getString("patient_fname"),
            rs.getString("patient_lname"),
            rs.getString("patient_nickname"),
            rs.getString("gender"),
            rs.getString("gender_detail"),
            rs.getString("phone"),
            rs.getLong("branch_id"),
            rs.getString("branch_code"),
            rs.getString("branch_name"),
            rs.getString("national_id"),
            rs.getString("passport_no"),
            rs.getObject("birthdate", LocalDate.class),
            rs.getString("blood_group"),
            rs.getString("nationality"),
            rs.getString("address"),
            rs.getObject("registered_at", LocalDateTime.class),
            rs.getLong("registered_by"),
            (Long) rs.getObject("customer_group_value_id"),
            rs.getString("customer_group_name"),
            (Long) rs.getObject("referral_channel_value_id"),
            rs.getString("referral_channel_name"),
            (Long) rs.getObject("insurance_company_value_id"),
            rs.getString("insurance_company_name")
    );

    public DatabaseV2PatientRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String generateHn(Long branchId, String branchCode, LocalDate date) {
        int year2Digit = date.getYear() % 100;
        int month2Digit = date.getMonthValue();
        String yearMonth = String.format("%02d%02d", year2Digit, month2Digit);

        Integer nextRunningNo = jdbc.queryForObject(
                """
                insert into hn_sequence (branch_id, year_month, last_running_no, last_running_at, created_at)
                values (?, ?, 1, current_timestamp, current_timestamp)
                on conflict (branch_id, year_month)
                do update set last_running_no = hn_sequence.last_running_no + 1,
                              last_running_at = current_timestamp
                returning last_running_no
                """,
                Integer.class, branchId, yearMonth
        );
        return String.format("%02d%s%02d%04d", year2Digit, branchCode.trim(), month2Digit, nextRunningNo);
    }

    public Optional<PatientRecord> findById(Long id) {
        return jdbc.query(SELECT_PATIENT + " where p.patient_id = ?", ROW_MAPPER, id).stream().findFirst();
    }

    public Optional<PatientRecord> findByHn(String hn) {
        return jdbc.query(SELECT_PATIENT + " where p.hn = ?", ROW_MAPPER, hn.trim()).stream().findFirst();
    }

    public Optional<PatientRecord> findByNationalId(String nationalId) {
        if (nationalId == null || nationalId.isBlank()) return Optional.empty();
        return jdbc.query(SELECT_PATIENT + " where p.national_id = ?", ROW_MAPPER, nationalId.trim()).stream().findFirst();
    }

    public Optional<PatientRecord> findByPassportNo(String passportNo) {
        if (passportNo == null || passportNo.isBlank()) return Optional.empty();
        return jdbc.query(SELECT_PATIENT + " where upper(p.passport_no) = upper(?)", ROW_MAPPER, passportNo.trim()).stream().findFirst();
    }

    public List<PatientRecord> search(String query, String branchCode, int limit, int offset) {
        StringBuilder sql = new StringBuilder(SELECT_PATIENT);
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (branchCode != null && !branchCode.isBlank()) {
            conditions.add("b.branch_code = ?");
            params.add(branchCode.trim().toUpperCase());
        }

        if (query != null && !query.isBlank()) {
            String q = "%" + query.trim().toLowerCase() + "%";
            conditions.add("(lower(p.hn) like ? or lower(p.patient_fname) like ? or lower(p.patient_lname) like ? or p.phone like ? or p.national_id like ? or lower(p.passport_no) like ?)");
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }

        if (!conditions.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", conditions));
        }

        sql.append(" order by p.patient_fname, p.patient_lname limit ? offset ?");
        params.add(limit);
        params.add(offset);

        return jdbc.query(sql.toString(), ROW_MAPPER, params.toArray());
    }

    public int countSearch(String query, String branchCode) {
        StringBuilder sql = new StringBuilder("select count(*) from patient p join branch b on b.branch_id = p.branch_id");
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        if (branchCode != null && !branchCode.isBlank()) {
            conditions.add("b.branch_code = ?");
            params.add(branchCode.trim().toUpperCase());
        }

        if (query != null && !query.isBlank()) {
            String q = "%" + query.trim().toLowerCase() + "%";
            conditions.add("(lower(p.hn) like ? or lower(p.patient_fname) like ? or lower(p.patient_lname) like ? or p.phone like ? or p.national_id like ? or lower(p.passport_no) like ?)");
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
            params.add(q);
        }

        if (!conditions.isEmpty()) {
            sql.append(" where ").append(String.join(" and ", conditions));
        }

        Integer count = jdbc.queryForObject(sql.toString(), Integer.class, params.toArray());
        return count != null ? count : 0;
    }

    public PatientRecord create(
            String hn, String customerType, String title, String fname, String lname,
            String gender, String genderDetail, String nickname, String phone,
            Long branchId, String nationalId, String passportNo, LocalDate birthdate,
            String bloodGroup, String nationality, String address, Long registeredBy,
            Long customerGroupValueId, Long referralChannelValueId, Long insuranceCompanyValueId
    ) {
        Long id = jdbc.queryForObject(
                """
                insert into patient (
                    hn, customer_type, title, patient_fname, patient_lname,
                    gender, gender_detail, patient_nickname, phone, branch_id,
                    national_id, passport_no, birthdate, blood_group, nationality, address,
                    registered_by, customer_group_value_id, referral_channel_value_id, insurance_company_value_id
                ) values (
                    ?, cast(? as customer_type_enum), cast(? as title_enum), ?, ?,
                    cast(? as gender_enum), ?, ?, ?, ?,
                    ?, ?, ?, cast(? as blood_group_enum), ?, ?,
                    ?, ?, ?, ?
                ) returning patient_id
                """,
                Long.class,
                hn, customerType, title, fname, lname,
                gender, genderDetail, nickname, phone, branchId,
                nationalId, passportNo, birthdate, bloodGroup, nationality, address,
                registeredBy, customerGroupValueId, referralChannelValueId, insuranceCompanyValueId
        );
        return findById(id).orElseThrow();
    }

    public PatientRecord update(
            Long id, String title, String fname, String lname,
            String gender, String genderDetail, String nickname, String phone,
            String nationalId, String passportNo, LocalDate birthdate,
            String bloodGroup, String nationality, String address, Long updatedBy,
            Long customerGroupValueId, Long referralChannelValueId, Long insuranceCompanyValueId
    ) {
        jdbc.update(
                """
                update patient set
                    title = cast(? as title_enum), patient_fname = ?, patient_lname = ?,
                    gender = cast(? as gender_enum), gender_detail = ?, patient_nickname = ?, phone = ?,
                    national_id = ?, passport_no = ?, birthdate = ?, blood_group = cast(? as blood_group_enum),
                    nationality = ?, address = ?, patient_update_at = ?, patient_update_by = ?,
                    customer_group_value_id = ?, referral_channel_value_id = ?, insurance_company_value_id = ?
                where patient_id = ?
                """,
                title, fname, lname,
                gender, genderDetail, nickname, phone,
                nationalId, passportNo, birthdate, bloodGroup,
                nationality, address, LocalDateTime.now(), updatedBy,
                customerGroupValueId, referralChannelValueId, insuranceCompanyValueId,
                id
        );
        return findById(id).orElseThrow();
    }
}
