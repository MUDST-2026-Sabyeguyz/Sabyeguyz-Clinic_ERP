package com.sabyeguyz.clinic.databasev2.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2Branch;
import com.sabyeguyz.clinic.databasev2.branch.DatabaseV2BranchRepository;
import com.sabyeguyz.clinic.databasev2.common.CurrentActorProvider;
import com.sabyeguyz.clinic.databasev2.common.DatabaseV2ConflictException;
import com.sabyeguyz.clinic.databasev2.masterdata.DatabaseV2MasterDataRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class DatabaseV2PatientControllerTest {

    private DatabaseV2PatientRepository patientRepository;
    private DatabaseV2BranchRepository branchRepository;
    private DatabaseV2MasterDataRepository masterDataRepository;
    private CurrentActorProvider actorProvider;
    private DatabaseV2PatientController controller;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        patientRepository = mock(DatabaseV2PatientRepository.class);
        branchRepository = mock(DatabaseV2BranchRepository.class);
        masterDataRepository = mock(DatabaseV2MasterDataRepository.class);
        actorProvider = mock(CurrentActorProvider.class);
        auth = mock(Authentication.class);

        controller = new DatabaseV2PatientController(
                patientRepository, branchRepository, masterDataRepository, actorProvider,
                Clock.fixed(Instant.parse("2026-09-30T17:30:00Z"), ZoneId.of("Asia/Bangkok"))
        , org.mockito.Mockito.mock(com.sabyeguyz.clinic.databasev2.security.BranchAccessGuard.class));

        when(masterDataRepository.findByCategory("Customer Group")).thenReturn(List.of(
                new DatabaseV2MasterDataRepository.MasterDataRecord(1L, 1L, "Customer Group", "Thai", "Thai", true, 0),
                new DatabaseV2MasterDataRepository.MasterDataRecord(1L, 1L, "Customer Group", "ทั่วไป", "ลูกค้าทั่วไป", true, 0)));

        when(actorProvider.getActorIdOrDefault(any(), any())).thenReturn(1L);

        DatabaseV2Branch branch = new DatabaseV2Branch(1L, "01", "Main Branch", "123 St", "021234567", true);
        when(branchRepository.findByCode("01")).thenReturn(Optional.of(branch));
    }

    @Test
    void registerThaiPatientGeneratesCorrectHn() {
        String hn = "2610010001";
        when(patientRepository.generateHn(eq(1L), eq("01"), eq(LocalDate.of(2026, 10, 1)))).thenReturn(hn);
        when(patientRepository.findByNationalId("1234567890123")).thenReturn(Optional.empty());

        DatabaseV2PatientRepository.PatientRecord record = new DatabaseV2PatientRepository.PatientRecord(
                10L, hn, "Thai", "นาย", "สมชาย", "ใจดี", "ชาย", "Male", null, "0812345678",
                1L, "01", "Main Branch", "1234567890123", null, LocalDate.of(1990, 1, 1),
                "O", "ไทย", "Bangkok", LocalDateTime.now(), 1L, null, null, null, null, null, null
        );
        when(patientRepository.create(
                eq(hn), eq("Thai"), eq("นาย"), eq("สมชาย"), eq("ใจดี"),
                eq("Male"), any(), eq("ชาย"), eq("0812345678"),
                eq(1L), eq("1234567890123"), any(), any(), eq("O"),
                eq("ไทย"), eq("Bangkok"), eq(1L), any(), any(), any()
        )).thenReturn(record);

        DatabaseV2PatientController.RegisterRequest request = new DatabaseV2PatientController.RegisterRequest(
                "01", null, null, null, "นาย", "สมชาย ใจดี", "สมชาย", "ใจดี",
                LocalDate.of(1990, 1, 1), "ชาย", "0812345678", "NATIONAL_ID", "1234567890123",
                "Bangkok", false, false, "", "", "", "", true, "ชาย", "O", "ไทย", "Thai", null, null, "Thai"
        );

        DatabaseV2PatientController.PatientResponse response = controller.register(request, auth);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.hn()).isEqualTo(hn);
        assertThat(response.hn()).matches("^[0-9]{10}$");
        assertThat(response.fullName()).isEqualTo("สมชาย ใจดี");
    }

    @Test
    void rejectsDuplicateNationalId() {
        DatabaseV2PatientRepository.PatientRecord existing = mock(DatabaseV2PatientRepository.PatientRecord.class);
        when(patientRepository.findByNationalId("1234567890123")).thenReturn(Optional.of(existing));

        DatabaseV2PatientController.RegisterRequest request = new DatabaseV2PatientController.RegisterRequest(
                "01", null, null, null, "นาย", "สมชาย ใจดี", "สมชาย", "ใจดี",
                LocalDate.of(1990, 1, 1), "ชาย", "0812345678", "NATIONAL_ID", "1234567890123",
                "Bangkok", false, false, "", "", "", "", true, "ชาย", "O", "ไทย", "Thai", null, null, "Thai"
        );

        assertThatThrownBy(() -> controller.register(request, auth))
                .isInstanceOf(DatabaseV2ConflictException.class)
                .hasMessageContaining("เลขประจำตัวประชาชนนี้มีอยู่ในระบบแล้ว");
    }

    @Test
    void rejectsInvalidNationalIdLengthForThai() {
        DatabaseV2PatientController.RegisterRequest request = new DatabaseV2PatientController.RegisterRequest(
                "01", null, null, null, "นาย", "สมชาย ใจดี", "สมชาย", "ใจดี",
                LocalDate.of(1990, 1, 1), "ชาย", "0812345678", "NATIONAL_ID", "12345",
                "Bangkok", false, false, "", "", "", "", true, "ชาย", "O", "ไทย", "Thai", null, null, "Thai"
        );

        assertThatThrownBy(() -> controller.register(request, auth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("เลขประจำตัวประชาชนต้องเป็นตัวเลข 13 หลัก");
    }

    @Test
    void permitsOptionalThaiIdentityAndPersistsNoFabricatedBirthday() {
        String hn = "2610010002";
        when(patientRepository.generateHn(eq(1L), eq("01"), eq(LocalDate.of(2026, 10, 1)))).thenReturn(hn);
        DatabaseV2PatientRepository.PatientRecord record = new DatabaseV2PatientRepository.PatientRecord(
                11L, hn, "Thai", "นางสาว", "Ada", "Lovelace Byron", null, "Female", null, "0812345678",
                1L, "01", "Main Branch", null, null, null, null, "ไทย", null, LocalDateTime.now(), 1L,
                null, null, null, null, null, null
        );
        when(patientRepository.create(eq(hn), eq("Thai"), eq("นางสาว"), eq("Ada"), eq("Lovelace Byron"),
                eq("Female"), any(), isNull(), eq("0812345678"), eq(1L), isNull(), isNull(), isNull(),
                isNull(), eq("ไทย"), isNull(), eq(1L), any(), any(), any())).thenReturn(record);
        DatabaseV2PatientController.RegisterRequest request = new DatabaseV2PatientController.RegisterRequest(
                "01", null, null, null, "นางสาว", null, "Ada", "Lovelace Byron", null, "หญิง",
                "0812345678", "NATIONAL_ID", "   ", "   ", false, false, "", "", "", "", true,
                "  ", "ไม่ทราบ", "ไทย", "ลูกค้าทั่วไป", null, null, "Thai"
        );

        controller.register(request, auth);

        verify(patientRepository).create(eq(hn), eq("Thai"), eq("นางสาว"), eq("Ada"), eq("Lovelace Byron"),
                eq("Female"), any(), isNull(), eq("0812345678"), eq(1L), isNull(), isNull(), isNull(),
                isNull(), eq("ไทย"), isNull(), eq(1L), any(), any(), any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"Foreigner,Canada,PASSPORT,Foreigner", "Thai,ต่างชาติ,PASSPORT,Thai", ",Canada,PASSPORT,Foreigner", ",ไทย,NATIONAL_ID,Thai"})
    void explicitTypeWinsAndLegacyInferenceDoesNotRequireDob(String type, String nationality, String document, String expected) {
        var record = mock(DatabaseV2PatientRepository.PatientRecord.class);
        when(patientRepository.create(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(record);
        var request = new DatabaseV2PatientController.RegisterRequest(
                "01", null, null, null, "นาย", null, "Alex", "Example", null, "ชาย", "0812345678",
                document, "", null, null, null, null, null, null, null, null, null,
                null, nationality, null, null, null, type);
        controller.register(request, auth);
        verify(patientRepository).create(any(), eq(expected), any(), eq("Alex"), eq("Example"), any(), any(),
                any(), any(), any(), isNull(), isNull(), isNull(), any(), eq(nationality), any(), any(), any(), any(), any());
    }

    @Test
    void searchReturnsPaginatedResponse() {
        DatabaseV2PatientRepository.PatientRecord r = new DatabaseV2PatientRepository.PatientRecord(
                1L, "2601090001", "Thai", "นาย", "สมชาย", "ใจดี", null, "Male", null, "0812345678",
                1L, "01", "Main Branch", "1234567890123", null, LocalDate.of(1990, 1, 1),
                "O", "ไทย", "Bangkok", LocalDateTime.now(), 1L, null, null, null, null, null, null
        );

        when(patientRepository.search(eq("สมชาย"), eq("01"), eq(20), eq(0)))
                .thenReturn(List.of(r));
        when(patientRepository.countSearch(eq("สมชาย"), eq("01"))).thenReturn(1);

        DatabaseV2PatientController.PageResponse page = controller.search("สมชาย", "01", "nameAsc", 0, 20);

        assertThat(page.totalItems()).isEqualTo(1);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).hn()).isEqualTo("2601090001");
    }

    @Test
    void searchRejectsUnboundedOrNegativePagination() {
        assertThatThrownBy(() -> controller.search("", null, "nameAsc", -1, 20))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("page");
        assertThatThrownBy(() -> controller.search("", null, "nameAsc", 0, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size");
        assertThatThrownBy(() -> controller.search("", null, "nameAsc", 0, 101))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size");
        assertThatThrownBy(() -> controller.search("", null, "nameAsc", Integer.MAX_VALUE, 100))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("page");
    }
}
