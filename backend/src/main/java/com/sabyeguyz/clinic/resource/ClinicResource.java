package com.sabyeguyz.clinic.resource;
import com.sabyeguyz.clinic.branch.Branch; import jakarta.persistence.*; import java.time.Instant;
@Entity @Table(name="clinic_resources") public class ClinicResource {
 @Id @GeneratedValue(strategy=GenerationType.SEQUENCE,generator="resource_gen") @SequenceGenerator(name="resource_gen",sequenceName="clinic_resource_id_seq",allocationSize=1) private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="branch_id") private Branch branch;
 @Column(nullable=false,unique=true) private String code; @Column(nullable=false) private String name; @Column(nullable=false) private String type; @Column(nullable=false) private String status;
 private boolean active=true; @Column(name="created_at") private Instant createdAt=Instant.now(); @Column(name="updated_at") private Instant updatedAt=createdAt;
 protected ClinicResource(){} public ClinicResource(Branch b,String c,String n,String t,String s){branch=b;update(c,n,t,s);}
 public void update(String c,String n,String t,String s){code=c;name=n;type=t;status=s;updatedAt=Instant.now();} public void deactivate(){active=false;updatedAt=Instant.now();} public void activate(){active=true;updatedAt=Instant.now();}
 public Long getId(){return id;} public Branch getBranch(){return branch;} public String getCode(){return code;} public String getName(){return name;} public String getType(){return type;} public String getStatus(){return status;} public boolean isActive(){return active;}
}
