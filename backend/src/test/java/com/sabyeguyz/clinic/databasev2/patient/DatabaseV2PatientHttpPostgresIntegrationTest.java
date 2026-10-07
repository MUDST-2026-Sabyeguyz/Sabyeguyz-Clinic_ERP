package com.sabyeguyz.clinic.databasev2.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sabyeguyz.clinic.databasev2.common.PostgresIntegrationTestSupport;
import com.sabyeguyz.clinic.databasev2.commission.DatabaseV2CommissionRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real servlet HTTP, ordinary JWT login/security and Spring AOP transactions on PostgreSQL. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("database-v2")
@Import(DatabaseV2PatientHttpPostgresIntegrationTest.BusinessClock.class)
class DatabaseV2PatientHttpPostgresIntegrationTest {
    static final String PASSWORD = UUID.randomUUID().toString();
    static final String SIGNING_KEY = UUID.randomUUID() + "-" + UUID.randomUUID();
    static String env(String key, String fallback) { return System.getenv().getOrDefault(key, fallback); }
    static String url() { return "jdbc:postgresql://" + env("POSTGRES_HOST", "localhost") + ":"
            + env("POSTGRES_PORT", "5432") + "/" + env("POSTGRES_DB", "clinic_erp"); }

    @BeforeAll
    static void requireConnection() {
        try (var connection = java.sql.DriverManager.getConnection(url(), env("POSTGRES_USER", "clinic"),
                env("POSTGRES_PASSWORD", "local-verification-password"))) {
            assertThat(connection.isValid(5)).isTrue();
        } catch (Exception failure) {
            PostgresIntegrationTestSupport.handleSetupFailure("HTTP PostgreSQL connection setup failed", failure);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DatabaseV2PatientHttpPostgresIntegrationTest::url);
        registry.add("spring.datasource.username", () -> env("POSTGRES_USER", "clinic"));
        registry.add("spring.datasource.password", () -> env("POSTGRES_PASSWORD", "local-verification-password"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.flyway.target", () -> "latest");
        registry.add("app.jwt.secret", () -> SIGNING_KEY);
        registry.add("app.bootstrap.enabled", () -> false);
    }

    @TestConfiguration
    static class BusinessClock {
        @Bean @Primary Clock httpPatientClock() {
            return Clock.fixed(Instant.parse("2026-09-30T17:30:00Z"), ZoneId.of("Asia/Bangkok"));
        }
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired DatabaseV2PatientController patientController;
    @Autowired DatabaseV2CommissionRepository commissionRepository;
    @Autowired JwtEncoder encoder;
    final HttpClient client = HttpClient.newHttpClient();
    String managerToken;
    String therapistToken;
    final List<Long> patientIds = java.util.Collections.synchronizedList(new ArrayList<>());
    final List<Long> financialRuleIds = java.util.Collections.synchronizedList(new ArrayList<>());
    final List<Long> financialSchemeIds = java.util.Collections.synchronizedList(new ArrayList<>());
    final List<Long> financialClosingIds = java.util.Collections.synchronizedList(new ArrayList<>());
    final List<Long> financialServiceIds = java.util.Collections.synchronizedList(new ArrayList<>());
    final List<Long> temporaryBranchAccessIds = java.util.Collections.synchronizedList(new ArrayList<>());
    final List<Long> temporaryPaymentMethodIds = java.util.Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void fixturesAndNormalLogin() throws Exception {
        String hash = new BCryptPasswordEncoder().encode(PASSWORD);
        for (int i = 0; i < 2; i++) {
            jdbc.update("insert into app_user (user_id,username,password_hash,user_status,last_login_at,user_create_at) "
                    + "values (?,?,?,true,current_timestamp,current_timestamp) on conflict(user_id) do update set password_hash=excluded.password_hash",
                    870L + i, "fast01_" + (i == 0 ? "manager" : "therapist"), hash);
            jdbc.update("insert into role(role_id,role_name,role_create_by) values(?,?,870) on conflict(role_id) do nothing",
                    870L + i, i == 0 ? "Manager" : "Therapist");
            jdbc.update("insert into user_role(user_id,role_id) values(?,?) on conflict do nothing",870L+i,870L+i);
            jdbc.update("insert into branch(branch_id,branch_code,branch_name,address,phone,branch_status,branch_create_by) "
                    + "values(?,?,?,'Synthetic fixture','020000870',true,870) on conflict(branch_id) do nothing",
                    870L+i, i == 0 ? "86" : "85", "FAST01 fixture " + i);
        }
        jdbc.update("insert into user_branch_access(user_id,branch_id) values(870,870),(871,870) on conflict do nothing");
        managerToken = login("fast01_manager", "MANAGER");
        therapistToken = login("fast01_therapist", "THERAPIST");
        assertThat(AopUtils.isAopProxy(patientController)).isTrue();
    }

    @AfterEach
    void cleanupOnlyOwnedPatients() {
        patientIds.forEach(id -> {
            List<Long> courseIds = jdbc.queryForList("select c.course_id from course c join receipt r on r.receipt_id=c.receipt_id where r.patient_id=?",Long.class,id);
            for (Long courseId : courseIds) {
                List<Long> visitIds = jdbc.queryForList("select visit_id from course_visit_usage where course_id=?",Long.class,courseId);
                jdbc.update("delete from course_visit_usage where course_id=?",courseId);
                visitIds.forEach(visitId -> jdbc.update("delete from patient_visit where visit_id=?",visitId));
            jdbc.update("delete from course_ledger where course_id=?",courseId);
            jdbc.update("delete from course_shared_member where course_id=?",courseId);
            jdbc.update("delete from sales_transaction where course_id=?",courseId);
            jdbc.update("delete from course where course_id=?",courseId);
        }
            jdbc.update("delete from transaction_cancellation where transaction_id in (select transaction_id from sales_transaction where patient_id=?)",id);
            jdbc.update("delete from payment_transaction where transaction_id in (select transaction_id from sales_transaction where patient_id=?)",id);
            jdbc.update("delete from sales_transaction where patient_id=?",id);
            jdbc.update("delete from receipt where patient_id=?",id);
            jdbc.update("delete from patient_visit where appointment_id in (select appointment_id from appointment where patient_id=?)", id);
            jdbc.update("delete from appointment_status_history where appointment_id in (select appointment_id from appointment where patient_id=?)", id);
            jdbc.update("delete from appointment where patient_id=?", id);
            jdbc.update("delete from patient where patient_id=?", id);
        });
        financialClosingIds.forEach(id -> jdbc.update("delete from monthly_commission_closing where closing_id=?",id));
        financialRuleIds.forEach(id -> jdbc.update("delete from treatment_fee_rule where treatment_fee_rule_id=?",id));
        financialSchemeIds.forEach(id -> {
            jdbc.update("delete from monthly_commission_closing where commission_scheme_id=?",id);
            jdbc.update("delete from commission_tier where commission_scheme_id=?",id);
            jdbc.update("delete from commission_scheme where commission_scheme_id=?",id);
        });
        financialServiceIds.forEach(id -> jdbc.update("delete from service where service_id=?",id));
        temporaryBranchAccessIds.forEach(id -> jdbc.update("delete from user_branch_access where user_id=870 and branch_id=?",id));
        temporaryPaymentMethodIds.forEach(id -> jdbc.update("delete from payment_method where payment_method_id=?",id));
        patientIds.clear(); financialRuleIds.clear(); financialSchemeIds.clear(); financialClosingIds.clear(); financialServiceIds.clear(); temporaryBranchAccessIds.clear(); temporaryPaymentMethodIds.clear();
    }

    void appointmentFixtures() {
        for (long staff=880; staff<882; staff++) jdbc.update("""
                insert into staff(staff_id,staff_code,staff_fname,staff_lname,position,branch_id,staff_status,hire_date,staff_create_by)
                values(?,?,'FAST01','Therapist','Therapist',870,true,'2026-01-01',870)
                on conflict(staff_id) do update set staff_status=true
                """,staff,"FAST01-"+staff);
        jdbc.update("""
                insert into service(service_id,service_code,service_name,service_type,service_price,service_status,service_create_by)
                values(880,'FAST01-APPT','FAST01 Appointment','SingleVisit',100,true,870)
                on conflict(service_id) do update set service_status=true,default_duration_minutes=60
                """);
        jdbc.update("insert into room(room_id,branch_id,room_name,room_type,room_status,room_create_by) "
                + "values(880,870,'FAST01 Room','ROOM',true,870) on conflict(room_id) do update set room_status=true");
    }

    Map<String,Object> appointment(long patientId,String start,long staff, boolean room) {
        var p=new HashMap<String,Object>();p.put("patientId",patientId);p.put("branchCode","86");
        p.put("therapistId",staff);p.put("serviceId",880);p.put("startAt",start);
        if(room)p.put("roomId",880);return p;
    }

    @Test
    void appointmentIntervalsBangkokMidnightResourcesAndTerminalHistory() throws Exception {
        appointmentFixtures();long patientId=create(patient("Thai")).path("id").asLong();
        var initial=appointment(patientId,"2026-10-29T16:30:15Z",880,true);
        var app=body(send("POST","/api/appointments",initial,managerToken),201);long id=app.path("id").asLong();
        assertThat(app.path("startAt").asText()).isEqualTo("2026-10-29T16:30:15Z");
        assertThat(app.path("endAt").asText()).isEqualTo("2026-10-29T17:30:15Z");
        assertThat(jdbc.queryForMap("select appointment_date,appointment_time,room_id from appointment where appointment_id=?",id))
                .containsEntry("appointment_date",java.sql.Date.valueOf("2026-10-29"))
                .containsEntry("appointment_time",java.sql.Time.valueOf("23:30:15")).containsEntry("room_id",880L);
        // Different start, crossing midnight, and a different therapist sharing the room still conflict.
        body(send("POST","/api/appointments",appointment(patientId,"2026-10-29T17:00:00Z",880,false),managerToken),409);
        body(send("POST","/api/appointments",appointment(patientId,"2026-10-29T17:00:00Z",881,true),managerToken),409);
        body(send("POST","/api/appointments",appointment(patientId,"2026-10-29T17:30:15Z",881,true),managerToken),201);
        body(send("POST","/api/appointments/"+id+"/start-service",Map.of(),managerToken),409);
        body(send("POST","/api/appointments/"+id+"/cancel",Map.of("reason","Cancelled by fixture"),managerToken),200);
        body(send("POST","/api/appointments/"+id+"/check-in",Map.of(),managerToken),409);
        assertThat(jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from patient_visit where appointment_id=?",Integer.class,id)).isZero();
        body(send("POST","/api/appointments",initial,managerToken),201);
    }

    @Test
    void concurrentHttpBookingsAndArrivalsCommitOneRowAndOneVisit() throws Exception {
        appointmentFixtures();long patientId=create(patient("Thai")).path("id").asLong();
        var executor=Executors.newFixedThreadPool(2);
        try {
            var booking=appointment(patientId,"2026-11-01T02:00:00Z",880,true);
            var futures=List.of(executor.submit(()->send("POST","/api/appointments",booking,managerToken)),
                    executor.submit(()->send("POST","/api/appointments",booking,managerToken)));
            var responses=new ArrayList<HttpResponse<String>>(); for(var future:futures)responses.add(future.get(30,TimeUnit.SECONDS));
            assertThat(responses.stream().map(HttpResponse::statusCode).toList()).containsExactlyInAnyOrder(201,409);
            long id=body(responses.stream().filter(r->r.statusCode()==201).findFirst().orElseThrow(),201).path("id").asLong();
            var arrivals=List.of(executor.submit(()->send("POST","/api/appointments/"+id+"/check-in",Map.of(),managerToken)),
                    executor.submit(()->send("POST","/api/appointments/"+id+"/check-in",Map.of(),managerToken)));
            var statuses=new ArrayList<Integer>();for(var future:arrivals)statuses.add(future.get(30,TimeUnit.SECONDS).statusCode());
            assertThat(statuses).containsExactlyInAnyOrder(200,409);
            assertThat(jdbc.queryForObject("select count(*) from appointment where patient_id=?",Integer.class,patientId)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from patient_visit where appointment_id=?",Integer.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id=?",Integer.class,id)).isEqualTo(2);
            body(send("POST","/api/appointments/"+id+"/start-service",Map.of(),managerToken),200);
            body(send("POST","/api/appointments/"+id+"/complete",Map.of(),managerToken),200);
            body(send("POST","/api/appointments/"+id+"/cancel",Map.of("reason","Too late"),managerToken),409);
            assertThat(jdbc.queryForObject("select cast(visit_status as varchar) from patient_visit where appointment_id=?",String.class,id)).isEqualTo("Completed");
        } finally { executor.shutdownNow(); }
    }

    @Test
    void concurrentHttpRescheduleAndBookingCommitOnlyOneAppointmentInTargetInterval() throws Exception {
        appointmentFixtures();long patientId=create(patient("Thai")).path("id").asLong();
        long originalId=body(send("POST","/api/appointments",appointment(patientId,"2027-01-03T02:00:00Z",880,true),managerToken),201).path("id").asLong();
        var executor=Executors.newFixedThreadPool(2);var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try {
            var reschedule=executor.submit(()->{ready.countDown();start.await();return send("PATCH","/api/appointments/"+originalId,Map.of("startAt","2027-01-03T04:00:00Z","reason","Concurrent move"),managerToken);});
            var booking=executor.submit(()->{ready.countDown();start.await();return send("POST","/api/appointments",appointment(patientId,"2027-01-03T04:00:00Z",880,true),managerToken);});
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            var moved=reschedule.get(30,TimeUnit.SECONDS);var booked=booking.get(30,TimeUnit.SECONDS);
            assertThat((moved.statusCode()==200) ^ (booked.statusCode()==201)).isTrue();
            if(booked.statusCode()==201)body(booked,201);else assertThat(booked.statusCode()).isEqualTo(409);
            if(moved.statusCode()==200)body(moved,200);else assertThat(moved.statusCode()).isEqualTo(409);
            var readback=body(send("GET","/api/appointments/"+originalId,null,managerToken),200);
            assertThat(readback.path("id").asLong()).isEqualTo(originalId);
            int overlapping=jdbc.queryForObject("""
                    select count(*) from appointment
                    where appointment_date + appointment_time < timestamp '2027-01-03 12:00:00'
                      and appointment_date + appointment_time + interval '60 minutes' > timestamp '2027-01-03 11:00:00'
                      and appointment_status not in ('Cancelled','NoShow')
                    """,Integer.class);
            assertThat(overlapping).isEqualTo(1);
            int originalHistory=jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id=?",Integer.class,originalId);
            if(moved.statusCode()==200){assertThat(readback.path("status").asText()).isEqualTo("RESCHEDULED");assertThat(readback.path("startAt").asText()).isEqualTo("2027-01-03T04:00:00Z");assertThat(originalHistory).isEqualTo(2);}
            else {assertThat(readback.path("status").asText()).isEqualTo("CONFIRMED");assertThat(readback.path("startAt").asText()).isEqualTo("2027-01-03T02:00:00Z");assertThat(originalHistory).isEqualTo(1);}
        } finally {start.countDown();executor.shutdownNow();}
    }

    @Test
    void rescheduleHistoryInsertFailureRollsBackAppointmentUpdate() throws Exception {
        appointmentFixtures();long patientId=create(patient("Thai")).path("id").asLong();
        long id=body(send("POST","/api/appointments",appointment(patientId,"2027-01-05T02:00:00Z",880,true),managerToken),201).path("id").asLong();
        var before=body(send("GET","/api/appointments/"+id,null,managerToken),200);
        assertThat(jdbc.queryForObject("select count(*) from appointment where appointment_date='2027-01-05' and appointment_time < time '12:00:00' and appointment_time + interval '60 minutes' > time '11:00:00'",Integer.class)).isZero();
        jdbc.execute("""
                create function fast01b_fail_reschedule_history() returns trigger language plpgsql as $$
                begin
                  if new.appointment_id = %d and new.to_status::text = 'Rescheduled' then
                    raise exception 'FAST01 isolated rollback injection';
                  end if;
                  return new;
                end;
                $$
                """.formatted(id));
        jdbc.execute("create trigger fast01b_fail_reschedule_history before insert on appointment_status_history for each row execute function fast01b_fail_reschedule_history()");
        try {
            var failure=send("PATCH","/api/appointments/"+id,Map.of("startAt","2027-01-05T04:00:00Z","reason","Forced audit-history failure"),managerToken);
            assertThat(failure.statusCode()).isEqualTo(500);
            var after=body(send("GET","/api/appointments/"+id,null,managerToken),200);
            assertThat(after.path("status").asText()).isEqualTo(before.path("status").asText());
            assertThat(after.path("startAt").asText()).isEqualTo(before.path("startAt").asText());
            assertThat(jdbc.queryForObject("select count(*) from appointment_status_history where appointment_id=?",Integer.class,id)).isEqualTo(1);
            assertThat(jdbc.queryForMap("select appointment_status,appointment_date,appointment_time from appointment where appointment_id=?",id))
                    .containsEntry("appointment_status","Confirmed")
                    .containsEntry("appointment_date",java.sql.Date.valueOf("2027-01-05"))
                    .containsEntry("appointment_time",java.sql.Time.valueOf("09:00:00"));
        } finally {
            jdbc.execute("drop trigger if exists fast01b_fail_reschedule_history on appointment_status_history");
            jdbc.execute("drop function if exists fast01b_fail_reschedule_history()");
        }
    }

    @Test
    void courseUseHttpPostsQ02AllocationsAndRepeatedCloseKeepsLockedValues() throws Exception {
        appointmentFixtures();
        long patientId=create(patient("Thai")).path("id").asLong();
        long courseServiceId=jdbc.queryForObject("""
                insert into service(service_code,service_name,service_type,service_price,service_status,
                    default_total_visits,default_bonus_visits,default_validity_days,service_create_by)
                values(?,?,'Course',5000,true,10,0,365,870) returning service_id
                """,Long.class,"F02"+UUID.randomUUID().toString().replace("-","").substring(0,16),"FAST02 Q02 course");
        financialServiceIds.add(courseServiceId);
        var purchase=body(send("POST","/api/course-purchases",Map.of("patientId",patientId,"courseId",courseServiceId,
                "branchCode","86","sellerEmployeeId",881),managerToken),201);
        long courseId=purchase.path("id").asLong();
        LocalDate monthStart=LocalDate.now().withDayOfMonth(1);
        var scheme=commissionRepository.createScheme("FAST02 locked fixture "+UUID.randomUUID(),"1",monthStart,null,
                List.of(new DatabaseV2CommissionRepository.TierInput(BigDecimal.ZERO,null,new BigDecimal("0.14"),1)),870L);
        financialSchemeIds.add(scheme.id());

        var firstClose=body(send("POST","/api/commission/closings/"+monthStart,null,managerToken),200);
        for(JsonNode row:firstClose) financialClosingIds.add(row.path("id").asLong());
        var closed=java.util.stream.StreamSupport.stream(firstClose.spliterator(),false)
                .filter(row -> row.path("employeeId").asLong()==881L).findFirst().orElseThrow();
        assertThat(closed.path("rate").decimalValue()).isEqualByComparingTo("0.14");
        assertThat(jdbc.queryForMap("select commission_status,locked_commission_rate,total_course_commission_pool,commission_allocation_per_visit from course where course_id=?",courseId))
                .containsEntry("commission_status","Locked");
        var locked=jdbc.queryForMap("select locked_commission_rate,total_course_commission_pool,commission_allocation_per_visit from course where course_id=?",courseId);
        assertThat((BigDecimal)locked.get("locked_commission_rate")).isEqualByComparingTo("0.14");
        assertThat((BigDecimal)locked.get("total_course_commission_pool")).isEqualByComparingTo("700.00");
        assertThat((BigDecimal)locked.get("commission_allocation_per_visit")).isEqualByComparingTo("70.00");

        var fee=body(send("POST","/api/commission/treatment-fees",Map.of("employeeId",880,"feeType","Fixed",
                "feeValue",45,"effectiveFrom",LocalDate.now().toString()),managerToken),200);
        long feeRuleId=fee.path("id").asLong(); financialRuleIds.add(feeRuleId);
        body(send("POST","/api/course-usages",Map.of("coursePurchaseId",courseId,"treatingStaffId",880,"note","Q02 substitute"),managerToken),201);
        var substitute=jdbc.queryForMap("""
                select treating_staff_id,case_owner_staff_id,commission_allocation_per_visit,gross_commission_allocation,
                       treatment_fee_rule_id,substitute_treatment_fee_amount,owner_net_commission_released
                from course_visit_usage where course_id=?
                """,courseId);
        assertThat(substitute).containsEntry("treating_staff_id",880L).containsEntry("case_owner_staff_id",881L).containsEntry("treatment_fee_rule_id",feeRuleId);
        assertThat((BigDecimal)substitute.get("commission_allocation_per_visit")).isEqualByComparingTo("70.00");
        assertThat((BigDecimal)substitute.get("gross_commission_allocation")).isEqualByComparingTo("70.00");
        assertThat((BigDecimal)substitute.get("substitute_treatment_fee_amount")).isEqualByComparingTo("45.00");
        assertThat((BigDecimal)substitute.get("owner_net_commission_released")).isEqualByComparingTo("25.00");
        body(send("DELETE","/api/commission/treatment-fees/"+feeRuleId,null,managerToken),200);
        body(send("POST","/api/course-usages",Map.of("coursePurchaseId",courseId,"treatingStaffId",881,"note","Q02 owner self"),managerToken),201);
        var usages=jdbc.query("""
                select treating_staff_id,case_owner_staff_id,gross_commission_allocation,treatment_fee_rule_id,
                       substitute_treatment_fee_amount,owner_net_commission_released
                from course_visit_usage where course_id=? order by visit_usage_id
                """,(rs,row)->{
                    Map<String,Object> item=new HashMap<>();
                    item.put("treating",rs.getLong("treating_staff_id"));item.put("owner",rs.getLong("case_owner_staff_id"));
                    item.put("gross",rs.getBigDecimal("gross_commission_allocation"));item.put("feeRule",rs.getObject("treatment_fee_rule_id"));
                    item.put("fee",rs.getBigDecimal("substitute_treatment_fee_amount"));item.put("net",rs.getBigDecimal("owner_net_commission_released"));
                    return item;
                },courseId);
        assertThat(usages).hasSize(2);
        assertThat(usages.get(0)).containsEntry("treating",880L).containsEntry("owner",881L).containsEntry("feeRule",feeRuleId);
        assertThat(usages.get(1)).containsEntry("treating",881L).containsEntry("owner",881L).containsEntry("feeRule",null);
        assertThat((BigDecimal)usages.get(1).get("gross")).isEqualByComparingTo("70.00");
        assertThat((BigDecimal)usages.get(1).get("fee")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal)usages.get(1).get("net")).isEqualByComparingTo("70.00");
        assertThat(jdbc.queryForObject("select count(*) from course_visit_usage where course_id=?",Integer.class,courseId)).isEqualTo(2);

        var closedSnapshot=jdbc.queryForMap("select monthly_course_sales,commission_scheme_id,commission_scheme_version,calculated_commission_rate,locked_commission_rate,closing_at,closed_by,closing_status from monthly_commission_closing where closing_id=?",closed.path("id").asLong());
        var courseSnapshot=jdbc.queryForMap("select locked_commission_rate,total_course_commission_pool,commission_allocation_per_visit,course_update_at from course where course_id=?",courseId);
        commissionRepository.updateScheme(scheme.id(),scheme.name(),"2",monthStart,null,
                List.of(new DatabaseV2CommissionRepository.TierInput(BigDecimal.ZERO,null,new BigDecimal("0.25"),1)),true,870L);
        assertThat(AopUtils.isAopProxy(commissionRepository)).isTrue();
        var executor=Executors.newFixedThreadPool(2);
        try {
            List<Future<HttpResponse<String>>> closes=List.of(executor.submit(()->send("POST","/api/commission/closings/"+monthStart,null,managerToken)),
                    executor.submit(()->send("POST","/api/commission/closings/"+monthStart,null,managerToken)));
            var responses=closes.stream().map(future->{try{return future.get(30,TimeUnit.SECONDS);}catch(Exception failure){throw new AssertionError(failure);}}).toList();
            var duplicateIds=responses.stream().map(response->{try{return java.util.stream.StreamSupport.stream(body(response,200).spliterator(),false)
                    .filter(row -> row.path("employeeId").asLong()==881L).findFirst().orElseThrow().path("id").asLong();}
                catch(Exception failure){throw new AssertionError(failure);}}).toList();
            assertThat(duplicateIds).containsExactly(closed.path("id").asLong(),closed.path("id").asLong());
        } finally { executor.shutdownNow(); }
        assertThat(jdbc.queryForMap("select monthly_course_sales,commission_scheme_id,commission_scheme_version,calculated_commission_rate,locked_commission_rate,closing_at,closed_by,closing_status from monthly_commission_closing where closing_id=?",closed.path("id").asLong())).containsAllEntriesOf(closedSnapshot);
        assertThat(jdbc.queryForMap("select locked_commission_rate,total_course_commission_pool,commission_allocation_per_visit,course_update_at from course where course_id=?",courseId)).containsAllEntriesOf(courseSnapshot);
        assertThat(jdbc.queryForObject("select locked_commission_rate from course where course_id=?",BigDecimal.class,courseId)).isEqualByComparingTo("0.14");
    }

    @Test
    void saleUsesAnAuthorizedBranchAndRejectsAnotherBranchBeforeWriting() throws Exception {
        appointmentFixtures();
        long patientId=create(patient("Thai")).path("id").asLong();
        long allowedBranchId=jdbc.queryForObject("""
                insert into branch(branch_code,branch_name,address,phone,branch_status,branch_create_by)
                values('87','FAST02 authorized branch','Synthetic','020000087',true,870)
                on conflict(branch_code) do update set branch_status=true
                returning branch_id
                """,Long.class);
        jdbc.update("insert into user_branch_access(user_id,branch_id) values(870,?) on conflict do nothing",allowedBranchId);
        temporaryBranchAccessIds.add(allowedBranchId);
        jdbc.queryForObject("""
                insert into branch(branch_code,branch_name,address,phone,branch_status,branch_create_by)
                values('88','FAST02 denied branch','Synthetic','020000088',true,870)
                on conflict(branch_code) do update set branch_status=true
                returning branch_id
                """,Long.class);
        long paymentMethodId=jdbc.queryForObject("""
                insert into payment_method(payment_method_name,payment_method_status,payment_method_create_by)
                values(?,true,870) returning payment_method_id
                """,Long.class,"FAST02-CASH-"+UUID.randomUUID().toString().substring(0,8));
        temporaryPaymentMethodIds.add(paymentMethodId);
        var payload=new HashMap<String,Object>();payload.put("patientId",patientId);payload.put("branchCode","87");
        payload.put("items",List.of(Map.of("type","SINGLE_VISIT","referenceId",880,"quantity",1)));
        payload.put("payments",List.of(Map.of("paymentMethodCode",jdbc.queryForObject("select payment_method_name from payment_method where payment_method_id=?",String.class,paymentMethodId),"amount",100)));
        var created=body(send("POST","/api/payments",payload,managerToken),201);
        assertThat(created.path("branchCode").asText()).isEqualTo("87");
        assertThat(created.path("total").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(created.path("paid").decimalValue()).isEqualByComparingTo("100.00");
        long receiptId=created.path("id").asLong();
        assertThat(jdbc.queryForMap("select branch_id,patient_id,receipt_no from receipt where receipt_id=?",receiptId))
                .containsEntry("branch_id",allowedBranchId).containsEntry("patient_id",patientId);
        long transactionId=jdbc.queryForObject("select transaction_id from sales_transaction where patient_id=? and service_id=880 order by transaction_id desc limit 1",Long.class,patientId);
        assertThat(jdbc.queryForObject("select branch_id from sales_transaction where transaction_id=?",Long.class,transactionId)).isEqualTo(allowedBranchId);
        assertThat(jdbc.queryForMap("select branch_id,amount,payment_status from payment_transaction where transaction_id=?",transactionId))
                .containsEntry("branch_id",allowedBranchId).containsEntry("payment_status",true);
        var readback=body(send("GET","/api/receipts/"+receiptId,null,managerToken),200);
        assertThat(readback.path("branchCode").asText()).isEqualTo("87");
        assertThat(readback.path("items").size()).isEqualTo(1);
        assertThat(readback.path("payments").get(0).path("amount").decimalValue()).isEqualByComparingTo("100.00");
        var visible=body(send("GET","/api/payments?page=0&size=100",null,managerToken),200);
        assertThat(visible.path("items").findValuesAsText("branchCode")).contains("87").doesNotContain("88");

        int receiptsBeforeDenied=jdbc.queryForObject("select count(*) from receipt where patient_id=?",Integer.class,patientId);
        int transactionsBeforeDenied=jdbc.queryForObject("select count(*) from sales_transaction where patient_id=?",Integer.class,patientId);
        payload.put("branchCode","88");
        body(send("POST","/api/payments",payload,managerToken),403);
        assertThat(jdbc.queryForObject("select count(*) from receipt where patient_id=?",Integer.class,patientId)).isEqualTo(receiptsBeforeDenied);
        assertThat(jdbc.queryForObject("select count(*) from sales_transaction where patient_id=?",Integer.class,patientId)).isEqualTo(transactionsBeforeDenied);
    }

    @Test
    void confirmedLineAndBillDiscountsPersistDistinctPayableAndCommissionBases() throws Exception {
        appointmentFixtures();
        long patientId=create(patient("Thai")).path("id").asLong();
        long serviceId=body(send("POST","/api/services",Map.of(
                "code","DISC-"+UUID.randomUUID().toString().substring(0,8),
                "name","FAST02 fixed discount fixture", "defaultDurationMinutes",60,"price",2000),managerToken),201).path("id").asLong();
        financialServiceIds.add(serviceId);
        long methodId=jdbc.queryForObject("insert into payment_method(payment_method_name,payment_method_status,payment_method_create_by) values(?,true,870) returning payment_method_id",Long.class,"FAST02-DISC-"+UUID.randomUUID().toString().substring(0,8));
        temporaryPaymentMethodIds.add(methodId);
        String method=jdbc.queryForObject("select payment_method_name from payment_method where payment_method_id=?",String.class,methodId);

        var linePayload=new HashMap<String,Object>();linePayload.put("patientId",patientId);linePayload.put("branchCode","86");
        linePayload.put("items",List.of(Map.of("type","SINGLE_VISIT","referenceId",serviceId,"quantity",1,"lineDiscountAmount",500)));
        linePayload.put("billDiscountAmount",0);linePayload.put("payments",List.of(Map.of("paymentMethodCode",method,"amount",1500)));
        var lineCreated=body(send("POST","/api/payments",linePayload,managerToken),201);long lineReceipt=lineCreated.path("id").asLong();
        assertThat(lineCreated.path("grossTotal").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(lineCreated.path("lineDiscountTotal").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(lineCreated.path("billDiscountAmount").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(lineCreated.path("total").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(lineCreated.path("paid").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(lineCreated.path("commissionBase").decimalValue()).isEqualByComparingTo("1500.00");
        var lineRead=body(send("GET","/api/receipts/"+lineReceipt,null,managerToken),200);
        assertThat(lineRead.path("items").get(0).path("grossAmount").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(lineRead.path("items").get(0).path("lineTotal").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(lineRead.path("items").get(0).path("commissionBase").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(jdbc.queryForMap("select gross_total,line_discount_total,bill_discount_amount,net_due from receipt where receipt_id=?",lineReceipt))
                .containsEntry("gross_total",new BigDecimal("2000.00")).containsEntry("line_discount_total",new BigDecimal("500.00"))
                .containsEntry("bill_discount_amount",new BigDecimal("0.00")).containsEntry("net_due",new BigDecimal("1500.00"));
        assertThat(jdbc.queryForMap("select gross_amount,line_discount_amount,amount from sales_transaction where receipt_id=? and receipt_link_status='VERIFIED'",lineReceipt))
                .containsEntry("gross_amount",new BigDecimal("2000.00")).containsEntry("line_discount_amount",new BigDecimal("500.00"))
                .containsEntry("amount",new BigDecimal("1500.00"));
        assertThat(jdbc.queryForObject("select sum(amount) from payment_transaction where transaction_id in (select transaction_id from sales_transaction where receipt_id=?) and payment_status=true",BigDecimal.class,lineReceipt)).isEqualByComparingTo("1500.00");
        System.out.println("FAST02_DISCOUNT_SQL line receipt="+jdbc.queryForMap("select gross_total,line_discount_total,bill_discount_amount,net_due from receipt where receipt_id=?",lineReceipt)
                +" transaction="+jdbc.queryForMap("select gross_amount,line_discount_amount,amount from sales_transaction where receipt_id=? and receipt_link_status='VERIFIED'",lineReceipt)
                +" paid="+jdbc.queryForObject("select sum(amount) from payment_transaction where transaction_id in (select transaction_id from sales_transaction where receipt_id=?) and payment_status=true",BigDecimal.class,lineReceipt));

        var billPayload=new HashMap<String,Object>();billPayload.put("patientId",patientId);billPayload.put("branchCode","86");
        billPayload.put("items",List.of(Map.of("type","SINGLE_VISIT","referenceId",serviceId,"quantity",1)));
        billPayload.put("billDiscountAmount",500);billPayload.put("payments",List.of(Map.of("paymentMethodCode",method,"amount",1500)));
        var billCreated=body(send("POST","/api/payments",billPayload,managerToken),201);long billReceipt=billCreated.path("id").asLong();
        assertThat(billCreated.path("grossTotal").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(billCreated.path("lineDiscountTotal").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(billCreated.path("billDiscountAmount").decimalValue()).isEqualByComparingTo("500.00");
        assertThat(billCreated.path("total").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(billCreated.path("paid").decimalValue()).isEqualByComparingTo("1500.00");
        assertThat(billCreated.path("commissionBase").decimalValue()).isEqualByComparingTo("2000.00");
        var billRead=body(send("GET","/api/receipts/"+billReceipt,null,managerToken),200);
        assertThat(billRead.path("items").get(0).path("commissionBase").decimalValue()).isEqualByComparingTo("2000.00");
        assertThat(jdbc.queryForMap("select gross_total,line_discount_total,bill_discount_amount,net_due from receipt where receipt_id=?",billReceipt))
                .containsEntry("gross_total",new BigDecimal("2000.00")).containsEntry("line_discount_total",new BigDecimal("0.00"))
                .containsEntry("bill_discount_amount",new BigDecimal("500.00")).containsEntry("net_due",new BigDecimal("1500.00"));
        assertThat(jdbc.queryForMap("select gross_amount,line_discount_amount,amount from sales_transaction where receipt_id=? and receipt_link_status='VERIFIED'",billReceipt))
                .containsEntry("gross_amount",new BigDecimal("2000.00")).containsEntry("line_discount_amount",new BigDecimal("0.00"))
                .containsEntry("amount",new BigDecimal("2000.00"));
        assertThat(jdbc.queryForObject("select sum(amount) from payment_transaction where transaction_id in (select transaction_id from sales_transaction where receipt_id=?) and payment_status=true",BigDecimal.class,billReceipt)).isEqualByComparingTo("1500.00");
        System.out.println("FAST02_DISCOUNT_SQL bill receipt="+jdbc.queryForMap("select gross_total,line_discount_total,bill_discount_amount,net_due from receipt where receipt_id=?",billReceipt)
                +" transaction="+jdbc.queryForMap("select gross_amount,line_discount_amount,amount from sales_transaction where receipt_id=? and receipt_link_status='VERIFIED'",billReceipt)
                +" paid="+jdbc.queryForObject("select sum(amount) from payment_transaction where transaction_id in (select transaction_id from sales_transaction where receipt_id=?) and payment_status=true",BigDecimal.class,billReceipt));
    }

    @Test
    void explicitReceiptLinkWinsOverCrossedTransactionIdForHttpReadListAndVoid() throws Exception {
        appointmentFixtures();
        long branchA=jdbc.queryForObject("insert into branch(branch_code,branch_name,address,phone,branch_status,branch_create_by) values('82','Receipt link A','Synthetic','020000082',true,870) on conflict(branch_code) do update set branch_status=true returning branch_id",Long.class);
        long branchB=jdbc.queryForObject("insert into branch(branch_code,branch_name,address,phone,branch_status,branch_create_by) values('83','Receipt link B','Synthetic','020000083',true,870) on conflict(branch_code) do update set branch_status=true returning branch_id",Long.class);
        jdbc.update("insert into user_branch_access(user_id,branch_id) values(870,?),(870,?) on conflict do nothing",branchA,branchB);
        long patientA=jdbc.queryForObject("insert into patient(hn,customer_type,title,patient_fname,patient_lname,gender,phone,branch_id,registered_by) values(?,cast(? as customer_type_enum),cast(? as title_enum),?,?,cast(? as gender_enum),?,? ,870) returning patient_id",Long.class,String.format("%010d",Math.abs(System.nanoTime()%10_000_000_000L)),"Thai","นาย","Link","A","Male","0890000082",branchA);
        long patientB=jdbc.queryForObject("insert into patient(hn,customer_type,title,patient_fname,patient_lname,gender,phone,branch_id,registered_by) values(?,cast(? as customer_type_enum),cast(? as title_enum),?,?,cast(? as gender_enum),?,? ,870) returning patient_id",Long.class,String.format("%010d",Math.abs(System.nanoTime()%10_000_000_000L)),"Thai","นาย","Link","B","Male","0890000083",branchB);
        long base=jdbc.queryForObject("select greatest(coalesce((select max(receipt_id) from receipt),0),coalesce((select max(transaction_id) from sales_transaction),0))+1000",Long.class);
        long receiptA=base, receiptB=base+1;
        jdbc.update("insert into receipt(receipt_id,receipt_no,patient_id,branch_id,sale_date,receipt_create_by,gross_total,net_due) values(?,?,?,?,current_date,870,50,50),(?,?,?,?,current_date,870,70,70)",receiptA,"RE-"+String.format("%07d",receiptA),patientA,branchA,receiptB,"RE-"+String.format("%07d",receiptB),patientB,branchB);
        long crossedTx=receiptB, secondTx=base+2, receiptBTx=base+3;
        String transactionSql="""
            insert into sales_transaction(transaction_id,receipt_id,patient_id,branch_id,service_id,transaction_type,quantity,unit_price,amount,transaction_date,transaction_status,transaction_create_by)
            values(?,?,?,?,880,'SingleVisit',1,?,?,current_timestamp,true,870)
            """;
        jdbc.update(transactionSql,crossedTx,receiptA,patientA,branchA,new BigDecimal("30"),new BigDecimal("30"));
        jdbc.update(transactionSql,secondTx,receiptA,patientA,branchA,new BigDecimal("20"),new BigDecimal("20"));
        jdbc.update(transactionSql,receiptBTx,receiptB,patientB,branchB,new BigDecimal("70"),new BigDecimal("70"));
        long paymentMethod=jdbc.queryForObject("insert into payment_method(payment_method_name,payment_method_status,payment_method_create_by) values(?,true,870) returning payment_method_id",Long.class,"B3-CASH-"+UUID.randomUUID().toString().substring(0,8));
        jdbc.update("insert into payment_transaction(patient_id,branch_id,transaction_id,amount,payment_method_id,payment_date,payment_status,payment_create_by) values(?,?,?,?,?,current_timestamp,true,870),(?,?,?,?,?,current_timestamp,true,870),(?,?,?,?,?,current_timestamp,true,870)",
                patientA,branchA,crossedTx,new BigDecimal("30"),paymentMethod, patientA,branchA,secondTx,new BigDecimal("20"),paymentMethod, patientB,branchB,receiptBTx,new BigDecimal("70"),paymentMethod);

        var readA=body(send("GET","/api/receipts/"+receiptA,null,managerToken),200);
        var readB=body(send("GET","/api/receipts/"+receiptB,null,managerToken),200);
        assertThat(readA.path("total").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(readA.path("items").size()).isEqualTo(2);
        assertThat(readA.path("payments").size()).isEqualTo(2);
        assertThat(readB.path("total").decimalValue()).isEqualByComparingTo("70.00");
        assertThat(readB.path("items").size()).isEqualTo(1);
        assertThat(readB.path("payments").size()).isEqualTo(1);
        var list=body(send("GET","/api/payments?page=0&size=100",null,managerToken),200);
        var summaryB=java.util.stream.StreamSupport.stream(list.path("items").spliterator(),false)
                .filter(item->item.path("id").asLong()==receiptB).findFirst().orElseThrow();
        assertThat(summaryB.path("total").decimalValue()).isEqualByComparingTo("70.00");
        body(send("POST","/api/payments/"+receiptB+"/reverse",Map.of("reason","B3 crossed-ID isolation"),managerToken),200);
        assertThat(jdbc.queryForObject("select count(*) from payment_transaction where transaction_id in (?,?) and payment_status=true",Integer.class,crossedTx,secondTx)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from payment_transaction where transaction_id=? and payment_status=false",Integer.class,receiptBTx)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from transaction_cancellation where transaction_id=?",Integer.class,crossedTx)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from transaction_cancellation where transaction_id=?",Integer.class,receiptBTx)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sales_transaction where transaction_id in (?,?) and transaction_status=true",Integer.class,crossedTx,secondTx)).isEqualTo(2);
        assertThat(body(send("GET","/api/receipts/"+receiptA,null,managerToken),200).path("status").asText()).isEqualTo("PAID");
    }

    @Test
    void confirmedSettingsCrudPersistsAndManagerOnlyWritesAreDeniedToTherapist() throws Exception {
        appointmentFixtures();
        var branchRequest=Map.of("code","84","name","FAST01B configured branch","address","A2 settings fixture","phone","020000084");
        var branch=body(send("POST","/api/branches",branchRequest,managerToken),201);long branchId=branch.path("id").asLong();
        var updatedBranchRequest=Map.of("code","84","name","FAST01B configured branch edited","address","A2 settings fixture updated","phone","020000085");
        body(send("PUT","/api/branches/"+branchId,updatedBranchRequest,managerToken),200);
        assertThat(body(send("GET","/api/branches/"+branchId,null,managerToken),200).path("name").asText()).isEqualTo("FAST01B configured branch edited");
        assertThat(jdbc.queryForMap("select branch_code,branch_name,address,phone,branch_status from branch where branch_id=?",branchId))
                .containsEntry("branch_code","84").containsEntry("branch_name","FAST01B configured branch edited")
                .containsEntry("address","A2 settings fixture updated").containsEntry("phone","020000085").containsEntry("branch_status",true);
        body(send("PUT","/api/branches/"+branchId,updatedBranchRequest,therapistToken),403);
        var accessibleBranches=body(send("GET","/api/branches?accessible=true",null,therapistToken),200);
        assertThat(accessibleBranches.findValuesAsText("code")).doesNotContain("84");

        var staff=body(send("POST","/api/staff",Map.of("employeeCode","A2B-STAFF-84","fullName","A2B Therapist","branchId",branchId,"position","Therapist"),managerToken),201);
        long staffId=staff.path("id").asLong();
        body(send("PUT","/api/staff/"+staffId,Map.of("employeeCode","A2B-STAFF-84","fullName","A2B Therapist Edited","branchId",branchId,"position","Therapist"),managerToken),200);
        assertThat(body(send("GET","/api/staff/"+staffId,null,managerToken),200).path("fullName").asText()).isEqualTo("A2B Therapist Edited");
        assertThat(jdbc.queryForMap("select staff_fname,staff_lname,branch_id,staff_status from staff where staff_id=?",staffId))
                .containsEntry("staff_fname","A2B").containsEntry("staff_lname","Therapist Edited").containsEntry("branch_id",branchId).containsEntry("staff_status",true);
        body(send("PUT","/api/staff/"+staffId,Map.of("employeeCode","A2B-STAFF-84","fullName","Unauthorized Edit","branchId",branchId),therapistToken),403);

        var room=body(send("POST","/api/resources",Map.of("branchCode","84","name","A2B Room","type","ROOM","active",true),managerToken),201);
        long roomId=room.path("id").asLong();
        body(send("PUT","/api/resources/"+roomId,Map.of("branchCode","84","name","A2B Room Retired","type","ROOM","active",false),managerToken),200);
        assertThat(body(send("GET","/api/resources/"+roomId,null,managerToken),200).path("active").asBoolean()).isFalse();
        assertThat(jdbc.queryForMap("select branch_id,room_name,room_type,room_status from room where room_id=?",roomId))
                .containsEntry("branch_id",branchId).containsEntry("room_name","A2B Room Retired").containsEntry("room_type","ROOM").containsEntry("room_status",false);
        body(send("PATCH","/api/resources/"+roomId+"/activate",Map.of(),managerToken),200);
        body(send("PUT","/api/resources/"+roomId,Map.of("branchCode","84","name","Unauthorized Room Edit","type","ROOM","active",true),therapistToken),403);

        var payment=body(send("POST","/api/payment-methods",Map.of("code","A2B-PM-84","name","A2B configured payment","active",true),managerToken),201);
        long paymentId=payment.path("id").asLong();
        body(send("PUT","/api/payment-methods/"+paymentId,Map.of("code","A2B-PM-84","name","A2B configured payment edited","active",false),managerToken),200);
        var paymentList=body(send("GET","/api/payment-methods",null,managerToken),200);
        assertThat(paymentList.findValues("id").stream().anyMatch(value -> value.asLong()==paymentId)).isTrue();
        assertThat(jdbc.queryForMap("select payment_method_name,payment_method_status from payment_method where payment_method_id=?",paymentId))
                .containsEntry("payment_method_name","A2B configured payment edited").containsEntry("payment_method_status",false);
        body(send("DELETE","/api/payment-methods/"+paymentId,null,therapistToken),403);

        var fee=body(send("POST","/api/commission/treatment-fees",Map.of("employeeId",880,"feeType","Fixed","feeValue",45,"effectiveFrom","2026-01-01"),managerToken),200);
        long feeId=fee.path("id").asLong();
        var feeList=body(send("GET","/api/commission/treatment-fees",null,managerToken),200);
        assertThat(feeList.findValuesAsText("id")).contains(String.valueOf(feeId));
        var feeSql=jdbc.queryForMap("select staff_id,cast(fee_type as varchar) fee_type,fee_value,effective_from,treatment_fee_rule_status from treatment_fee_rule where treatment_fee_rule_id=?",feeId);
        assertThat(feeSql).containsEntry("staff_id",880L).containsEntry("fee_type","Fixed")
                .containsEntry("effective_from",java.sql.Date.valueOf("2026-01-01")).containsEntry("treatment_fee_rule_status",true);
        assertThat((java.math.BigDecimal)feeSql.get("fee_value")).isEqualByComparingTo("45");
        body(send("DELETE","/api/commission/treatment-fees/"+feeId,null,managerToken),200);
        assertThat(jdbc.queryForObject("select treatment_fee_rule_status from treatment_fee_rule where treatment_fee_rule_id=?",Boolean.class,feeId)).isFalse();
        body(send("POST","/api/commission/treatment-fees",Map.of("employeeId",880,"feeType","Fixed","feeValue",46,"effectiveFrom","2026-01-01"),therapistToken),403);

        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=current_schema() and table_name='app_user' and column_name in ('email','phone')",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=current_schema() and table_name='role_permission'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema=current_schema() and table_name='user_permission'",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from role_permission",Integer.class)).isZero();
        var users=body(send("GET","/api/users",null,managerToken),200);
        var managerRow=users.findParents("username").stream().filter(row -> row.path("username").asText().equals("fast01_manager")).findFirst().orElseThrow();
        assertThat(managerRow.path("email").isNull()).isTrue();
        assertThat(managerRow.path("phone").isNull()).isTrue();
    }

    @Test
    void appointmentServerPagingSearchAndConfiguredBranchActiveMasters() throws Exception {
        appointmentFixtures();var payload=patient("Thai");payload.put("lastName","AppointmentPaging");
        long patientId=create(payload).path("id").asLong();
        var ids=new ArrayList<Long>();
        for(int i=0;i<23;i++)ids.add(body(send("POST","/api/appointments",appointment(patientId,
                Instant.parse("2026-12-01T02:00:00Z").plusSeconds(3600L*i).toString(),880,false),managerToken),201).path("id").asLong());
        var first=body(send("GET","/api/appointments?query=AppointmentPaging&sort=oldest&page=0&size=20",null,managerToken),200);
        var second=body(send("GET","/api/appointments?query=AppointmentPaging&sort=oldest&page=1&size=20",null,managerToken),200);
        assertThat(first.path("totalItems").asInt()).isEqualTo(23);assertThat(second.path("totalItems").asInt()).isEqualTo(23);
        assertThat(first.path("items").size()).isEqualTo(20);assertThat(second.path("items").size()).isEqualTo(3);
        assertThat(second.path("items").get(0).path("id").asLong()).isEqualTo(ids.get(20));
        body(send("GET","/api/appointments?page=2147483647&size=100",null,managerToken),400);
        body(send("GET","/api/appointments?status=INVALID",null,managerToken),400);
        body(send("GET","/api/appointments?branchCode=85",null,therapistToken),200);
        var inaccessible=appointment(patientId,"2027-01-01T02:00:00Z",880,false);inaccessible.put("branchCode","85");
        body(send("POST","/api/appointments",inaccessible,managerToken),403);
        jdbc.update("update service set service_status=false where service_id=880");
        body(send("POST","/api/appointments",appointment(patientId,"2027-01-01T02:00:00Z",880,false),managerToken),400);
        assertThat(jdbc.queryForObject("select count(*) from appointment where patient_id=?",Integer.class,patientId)).isEqualTo(23);
    }

    @Test
    void configuredServiceDurationLocksAtBookingAndReschedulePreservesIt() throws Exception {
        appointmentFixtures();long patientId=create(patient("Thai")).path("id").asLong();
        var service=Map.of("code","FAST01-APPT","name","Configured thirty minutes","price",100,"defaultDurationMinutes",30);
        body(send("PUT","/api/services/880",service,therapistToken),403);
        assertThat(body(send("PUT","/api/services/880",service,managerToken),200).path("defaultDurationMinutes").asInt()).isEqualTo(30);
        assertThat(jdbc.queryForObject("select default_duration_minutes from service where service_id=880",Integer.class)).isEqualTo(30);
        var app=body(send("POST","/api/appointments",appointment(patientId,"2027-02-01T02:00:00Z",880,true),managerToken),201);
        long id=app.path("id").asLong();assertThat(app.path("endAt").asText()).isEqualTo("2027-02-01T02:30:00Z");
        body(send("PUT","/api/services/880",Map.of("code","FAST01-APPT","name","Configured ninety minutes","price",100,"defaultDurationMinutes",90),managerToken),200);
        assertThat(body(send("GET","/api/appointments/"+id,null,managerToken),200).path("endAt").asText()).isEqualTo("2027-02-01T02:30:00Z");
        body(send("POST","/api/appointments",appointment(patientId,"2027-02-01T02:15:00Z",880,false),managerToken),409);
        var next=body(send("POST","/api/appointments",appointment(patientId,"2027-02-01T02:30:00Z",880,false),managerToken),201);
        assertThat(next.path("endAt").asText()).isEqualTo("2027-02-01T04:00:00Z");
        body(send("PATCH","/api/appointments/"+id,Map.of("startAt","2027-02-01T03:00:00Z"),managerToken),409);
        assertThat(body(send("GET","/api/appointments/"+id,null,managerToken),200).path("status").asText()).isEqualTo("CONFIRMED");
        var moved=body(send("PATCH","/api/appointments/"+id,Map.of("startAt","2027-02-02T02:00:00Z"),managerToken),200);
        assertThat(moved.path("endAt").asText()).isEqualTo("2027-02-02T02:30:00Z");
        assertThat(jdbc.queryForObject("select duration_minutes from appointment where appointment_id=?",Integer.class,id)).isEqualTo(30);
        // History varchar failure must roll back the inserted appointment, including its locked duration.
        int before=jdbc.queryForObject("select count(*) from appointment where patient_id=?",Integer.class,patientId);
        var invalid=appointment(patientId,"2027-02-03T02:00:00Z",880,false);invalid.put("note","x".repeat(300));
        body(send("POST","/api/appointments",invalid,managerToken),409);
        assertThat(jdbc.queryForObject("select count(*) from appointment where patient_id=?",Integer.class,patientId)).isEqualTo(before);
    }

    @Test
    void postPutGetAndSqlProveFieldsOmittedNullAndServerOwnedIdentity() throws Exception {
        var payload = patient("Foreigner");
        payload.put("identityDocumentType", "PASSPORT");
        payload.put("identityNumber", " P-Alpha 9 ");
        JsonNode created = create(payload);
        long id = created.path("id").asLong();
        var get = body(send("GET", "/api/patients/" + id, null, managerToken), 200);
        assertThat(get.path("birthDate").isNull()).isTrue();
        assertThat(get.path("identityNumber").asText()).isEqualTo("P-Alpha 9");
        payload.put("birthDate", "1985-12-31");
        payload.put("address", "  Edited Address  ");
        payload.put("firstName", "Grace");
        payload.put("lastName", "Hopper Lovelace");
        payload.put("branchCode", "85");
        payload.put("patientType", "Thai");
        body(send("PUT", "/api/patients/" + id, payload, managerToken), 200);
        var updated = body(send("GET", "/api/patients/" + id, null, managerToken), 200);
        assertThat(updated.path("patientType").asText()).isEqualTo("Foreigner");
        assertThat(updated.path("branch").path("code").asText()).isEqualTo("86");
        assertThat(updated.path("hn").asText()).isEqualTo(created.path("hn").asText()).startsWith("268610");
        assertThat(updated.path("lastName").asText()).isEqualTo("Hopper Lovelace");
        var sql = jdbc.queryForMap("select patient_id,hn,branch_id,birthdate,address,patient_fname,patient_lname,passport_no from patient where patient_id=?",id);
        assertThat(sql.get("branch_id")).isEqualTo(870L);
        assertThat(sql.get("birthdate").toString()).isEqualTo("1985-12-31");
        assertThat(sql.get("address")).isEqualTo("Edited Address");
        assertThat(sql.get("patient_fname")).isEqualTo("Grace");
        assertThat(sql.get("patient_lname")).isEqualTo("Hopper Lovelace");
        assertThat(sql.get("passport_no")).isEqualTo("P-Alpha 9");
        payload.remove("birthDate");
        body(send("PUT", "/api/patients/"+id,payload,managerToken),200);
        assertThat(body(send("GET","/api/patients/"+id,null,managerToken),200).path("birthDate").asText()).isEqualTo("1985-12-31");
        payload.put("birthDate",null);
        body(send("PUT", "/api/patients/"+id,payload,managerToken),200);
        assertThat(body(send("GET","/api/patients/"+id,null,managerToken),200).path("birthDate").isNull()).isTrue();
        assertThat(jdbc.queryForMap("select birthdate from patient where patient_id=?",id).get("birthdate")).isNull();
    }

    @Test
    void explicitThaiWithPassportRemainsThaiAndLegacyCallerInfersOnlyWhenAbsent() throws Exception {
        var payload=patient("Thai");
        payload.put("nationality","ต่างชาติ");
        payload.put("identityDocumentType","PASSPORT");
        payload.put("identityNumber","TH-Passport");
        var created=create(payload);
        var result=body(send("GET","/api/patients/"+created.path("id").asLong(),null,managerToken),200);
        assertThat(result.path("patientType").asText()).isEqualTo("Thai");
        assertThat(result.path("identityNumber").asText()).isEqualTo("TH-Passport");
        payload.remove("patientType");payload.put("identityNumber","Legacy-Passport");
        var legacy=create(payload);
        assertThat(body(send("GET","/api/patients/"+legacy.path("id").asLong(),null,managerToken),200).path("patientType").asText()).isEqualTo("Foreigner");
    }

    @Test
    void invalidFieldsAndSuppliedInvalidIdDoNotSilentlyPersist() throws Exception {
        for (String field:List.of("title","firstName","lastName","gender","phone")) {
            var invalid=patient("Thai");invalid.put(field," ");
            body(send("POST","/api/patients",invalid,managerToken),400);
        }
        var invalid=patient("Thai");invalid.put("gender","invalid");
        body(send("POST","/api/patients",invalid,managerToken),400);
        var payload=patient("Thai");var created=create(payload);long id=created.path("id").asLong();
        payload.put("identityDocumentType","NATIONAL_ID");payload.put("identityNumber","123");
        body(send("PUT","/api/patients/"+id,payload,managerToken),400);
        assertThat(jdbc.queryForMap("select national_id from patient where patient_id=?",id).get("national_id")).isNull();
    }

    @Test
    void realSpringTransactionRollsBackHnAfterInsertFailureAndOverflowHasNoRow() throws Exception {
        var payload=patient("Thai");payload.put("address","X".repeat(300));
        int before=counter();long rows=count();
        body(send("POST","/api/patients",payload,managerToken),409);
        assertThat(counter()).isEqualTo(before);assertThat(count()).isEqualTo(rows);
        jdbc.update("insert into hn_sequence(branch_id,year_month,last_running_no) values(870,'2610',9999) "
                + "on conflict(branch_id,year_month) do update set last_running_no=9999");
        try {
            body(send("POST","/api/patients",patient("Thai"),managerToken),409);
            assertThat(counter()).isEqualTo(9999);assertThat(count()).isEqualTo(rows);
        } finally { jdbc.update("update hn_sequence set last_running_no=? where branch_id=870 and year_month='2610'",before); }
    }

    @Test
    void concurrentHttpRegistrationAllocatesUniqueHnAndSixCommittedRows() throws Exception {
        long before=count();int sequence=counter();
        var pool=Executors.newFixedThreadPool(6);
        try {
            var calls=new ArrayList<java.util.concurrent.Future<JsonNode>>();
            for(int i=0;i<6;i++) calls.add(pool.submit(()->create(patient("Thai"))));
            var hns=new java.util.HashSet<String>();
            for(var call:calls) hns.add(call.get(20,TimeUnit.SECONDS).path("hn").asText());
            assertThat(hns).hasSize(6).allMatch(hn->hn.startsWith("268610"));
            assertThat(count()).isEqualTo(before+6);assertThat(counter()).isEqualTo(sequence+6);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void normalAuthCorsExpiryPermissionAndBranchScopeAreEnforced() throws Exception {
        assertThat(send("GET","/api/patients",null,null).statusCode()).isEqualTo(401);
        var preflight=HttpRequest.newBuilder(URI.create(base()+"/api/patients"))
                .header("Origin","http://localhost:5173").header("Access-Control-Request-Method","POST")
                .header("Access-Control-Request-Headers","authorization,content-type").method("OPTIONS",HttpRequest.BodyPublishers.noBody()).build();
        var cors=client.send(preflight,HttpResponse.BodyHandlers.ofString());
        assertThat(cors.statusCode()).isEqualTo(200);
        assertThat(cors.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:5173");
        assertThat(send("POST","/api/master-data",Map.of("category","Customer Group","name","Denied"),therapistToken).statusCode()).isEqualTo(403);
        var branchChoices=body(send("GET","/api/branches?accessible=true",null,managerToken),200);
        assertThat(branchChoices.size()).isEqualTo(1);
        assertThat(branchChoices.get(0).path("code").asText()).isEqualTo("86");
        var denied=patient("Thai");denied.put("branchCode","85");
        assertThat(send("POST","/api/patients",denied,managerToken).statusCode()).isEqualTo(403);
        var deniedId=jdbc.queryForObject("insert into patient(hn,customer_type,title,patient_fname,patient_lname,gender,phone,branch_id,registered_by) "
                + "values('2697109998','Thai','นาย','Private','Fixture','Male','000',871,870) returning patient_id",Long.class);
        patientIds.add(deniedId);
        assertThat(send("GET","/api/patients/"+deniedId,null,managerToken).statusCode()).isEqualTo(403);
        assertThat(body(send("GET","/api/patients?query=Private",null,managerToken),200).path("totalItems").asInt()).isZero();
        var claims=JwtClaimsSet.builder().subject("870").claim("role","MANAGER")
                .issuedAt(Instant.now().minusSeconds(3600)).expiresAt(Instant.now().minusSeconds(600)).build();
        var expired=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        assertThat(send("GET","/api/patients",null,expired).statusCode()).isEqualTo(401);
        var bad=send("POST","/api/auth/login",Map.of("username","fast01_manager","password","wrong"),null);
        assertThat(bad.statusCode()).isEqualTo(401);
    }

    @Test
    void configuredMasterDataPersistsAndPatientReferencesItWithoutCreatingValues() throws Exception {
        String name="FAST01 "+UUID.randomUUID();
        var group=body(send("POST","/api/master-data",Map.of("category","Customer Group","name",name),managerToken),201);
        var payload=patient("Thai");payload.put("customerType",name);
        var created=create(payload);long id=created.path("id").asLong();
        assertThat(body(send("GET","/api/patients/"+id,null,managerToken),200).path("customerGroup").asText()).isEqualTo(name);
        assertThat(jdbc.queryForObject("select customer_group_value_id from patient where patient_id=?",Long.class,id)).isEqualTo(group.path("id").asLong());
        body(send("DELETE","/api/master-data/"+group.path("id").asLong(),null,managerToken),204);
        assertThat(body(send("GET","/api/master-data/"+group.path("id").asLong(),null,managerToken),200).path("active").asBoolean()).isFalse();
        body(send("PUT","/api/patients/"+id,payload,managerToken),200);
        assertThat(jdbc.queryForObject("select customer_group_value_id from patient where patient_id=?",Long.class,id)).isEqualTo(group.path("id").asLong());
        body(send("POST","/api/patients",payload,managerToken),400);
        payload.put("customerType","Unknown "+UUID.randomUUID());
        body(send("POST","/api/patients",payload,managerToken),400);
    }

    @Test
    void referralAndInsuranceConfiguredValuesPersistClearAndRetireWithoutLosingHistory() throws Exception {
        var payload=patient("Thai");
        var referral=body(send("POST","/api/master-data",Map.of("category","Referral Channel","name","Referral "+UUID.randomUUID()),managerToken),201);
        var insurance=body(send("POST","/api/master-data",Map.of("category","Insurance Company","name","Insurance "+UUID.randomUUID()),managerToken),201);
        payload.put("customerSource",referral.path("name").asText());payload.put("insuranceCompany",insurance.path("name").asText());
        long id=create(payload).path("id").asLong();
        assertThat(jdbc.queryForMap("select referral_channel_value_id,insurance_company_value_id from patient where patient_id=?",id))
                .containsEntry("referral_channel_value_id",referral.path("id").asLong()).containsEntry("insurance_company_value_id",insurance.path("id").asLong());
        var profile=body(send("GET","/api/patients/"+id,null,managerToken),200);
        assertThat(profile.path("customerSource").asText()).isEqualTo(referral.path("name").asText());
        assertThat(profile.path("insuranceCompany").asText()).isEqualTo(insurance.path("name").asText());
        body(send("DELETE","/api/master-data/"+insurance.path("id").asLong(),null,managerToken),204);
        body(send("PUT","/api/patients/"+id,payload,managerToken),200);
        body(send("POST","/api/patients",payload,managerToken),400);
        payload.put("customerSource","");payload.put("insuranceCompany","");
        body(send("PUT","/api/patients/"+id,payload,managerToken),200);
        assertThat(jdbc.queryForMap("select referral_channel_value_id,insurance_company_value_id from patient where patient_id=?",id).values()).containsOnlyNulls();
    }

    @Test
    void managedUserCanonicalRoleAndOmittedActiveDoNotResurrectDisabledAccount() throws Exception {
        String username="managed_"+UUID.randomUUID().toString().replace("-","");
        var request=new HashMap<String,Object>();request.put("username",username);request.put("password",PASSWORD);
        request.put("role","Therapist");request.put("employeeCode",username.substring(0,20));request.put("fullName","Managed Therapist");request.put("branchCode","86");
        body(send("POST","/api/users",request,therapistToken),403);
        var created=body(send("POST","/api/users",request,managerToken),201);long id=created.path("id").asLong();
        assertThat(created.path("role").asText()).isEqualTo("THERAPIST");login(username,"THERAPIST");
        assertThat(jdbc.queryForObject("select count(*) from user_branch_access where user_id=? and branch_id=870",Integer.class,id)).isEqualTo(1);
        body(send("DELETE","/api/users/"+id,null,managerToken),204);
        body(send("POST","/api/auth/login",Map.of("username",username,"password",PASSWORD),null),401);
        request.remove("password");request.put("fullName","Edited Inactive Therapist");
        assertThat(body(send("PUT","/api/users/"+id,request,managerToken),200).path("active").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select user_status from app_user where user_id=?",Boolean.class,id)).isFalse();
        body(send("POST","/api/auth/login",Map.of("username",username,"password",PASSWORD),null),401);
        request.put("role","RECEPTIONIST");body(send("PUT","/api/users/"+id,request,managerToken),400);
        assertThat(jdbc.queryForObject("select cast(position as varchar) from staff where staff_id=(select staff_id from app_user where user_id=?)",String.class,id)).isEqualTo("Therapist");
    }

    String base(){return "http://127.0.0.1:"+port;}
    Map<String,Object> patient(String type){
        var payload=new HashMap<String,Object>();
        payload.put("branchCode","86");payload.put("title","นาย");payload.put("firstName","FAST01");
        payload.put("lastName","Fixture");payload.put("gender","Male");payload.put("phone","0891234567");
        payload.put("patientType",type);return payload;
    }
    HttpResponse<String> send(String method,String path,Object payload,String token)throws Exception{
        var request=HttpRequest.newBuilder(URI.create(base()+path)).header("Content-Type","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        request.method(method,payload==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode body(HttpResponse<String> response,int status)throws Exception{
        assertThat(response.statusCode()).as("HTTP response status; body=%s", response.body()).isEqualTo(status);
        return response.body().isBlank()?json.nullNode():json.readTree(response.body());
    }
    String login(String username,String role)throws Exception{
        var result=body(send("POST","/api/auth/login",Map.of("username",username,"password",PASSWORD),null),200);
        assertThat(result.path("role").asText()).isEqualTo(role);return result.path("token").asText();
    }
    JsonNode create(Map<String,Object> payload)throws Exception{
        var result=body(send("POST","/api/patients",payload,managerToken),201);patientIds.add(result.path("id").asLong());return result;
    }
    int counter(){
        var rows=jdbc.queryForList("select last_running_no from hn_sequence where branch_id=870 and year_month='2610'",Integer.class);
        return rows.isEmpty()?0:rows.getFirst();
    }
    long count(){return jdbc.queryForObject("select count(*) from patient where branch_id=870",Long.class);}
}
