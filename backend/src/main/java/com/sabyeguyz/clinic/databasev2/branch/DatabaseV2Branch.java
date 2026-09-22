package com.sabyeguyz.clinic.databasev2.branch;

public record DatabaseV2Branch(
        Long id,
        String code,
        String name,
        String address,
        String phone,
        boolean active) {

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public boolean isActive() { return active; }
}
