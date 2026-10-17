package com.sabyeguyz.clinic.treatment;
import com.sabyeguyz.clinic.patient.*;
import com.sabyeguyz.clinic.appointment.*;
import com.sabyeguyz.clinic.staff.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly=true)
public class TreatmentService {
    private final TreatmentRepository treatments;
    private final PatientRepository patients;
    private final AppointmentRepository appointments;
    private final StaffRepository staff;
    public TreatmentService(TreatmentRepository treatments, PatientRepository patients,
            AppointmentRepository appointments, StaffRepository staff) {
        this.treatments=treatments; this.patients=patients; this.appointments=appointments; this.staff=staff;
    }
    @Transactional
    public TreatmentResponse create(TreatmentRequest request) {
        var patient=patients.findById(request.patientId()).orElseThrow(()->new PatientNotFoundException(request.patientId()));
        if(!patient.isActive()) throw new IllegalArgumentException("Patient is inactive");
        var appointment=request.appointmentId()==null?null:appointments.findById(request.appointmentId())
                .orElseThrow(()->new AppointmentNotFoundException(request.appointmentId()));
        if(appointment!=null && !appointment.getPatient().getId().equals(patient.getId()))
            throw new IllegalArgumentException("Appointment belongs to another patient");
        var practitioner=request.staffId()==null?null:staff.findById(request.staffId()).orElseThrow(StaffNotFoundException::new);
        if(practitioner!=null && (!practitioner.isActive() || !practitioner.getUser().isActive()))
            throw new IllegalArgumentException("Staff is inactive");
        return TreatmentResponse.from(treatments.save(new Treatment(patient,appointment,practitioner,request.service().trim(),request.note())));
    }
    public TreatmentResponse detail(long id) {
        return TreatmentResponse.from(treatments.findById(id).orElseThrow(()->new TreatmentNotFoundException(id)));
    }
    public Page<TreatmentResponse> history(Long patientId,int page,int size) {
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("page >= 0 and size between 1 and 100 required");
        var pageable=PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")));
        if(patientId==null) return treatments.findAll(pageable).map(TreatmentResponse::from);
        if(!patients.existsById(patientId)) throw new PatientNotFoundException(patientId);
        return treatments.findByPatientId(patientId,pageable).map(TreatmentResponse::from);
    }
}
