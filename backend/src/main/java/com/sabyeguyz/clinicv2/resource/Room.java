package com.sabyeguyz.clinicv2.resource;

import com.sabyeguyz.clinicv2.branch.Branch;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "room")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "room_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    /** e.g. Treatment Room, Exercise Area. */
    @Column(name = "room_name", length = 100, nullable = false)
    private String roomName;

    @Column(name = "room_type", length = 50)
    private String roomType;

    @Column(name = "room_status", nullable = false)
    private boolean roomStatus = true;

    @Column(name = "room_create_at", nullable = false, updatable = false)
    private Instant roomCreateAt;

    @Column(name = "room_create_by", nullable = false, updatable = false)
    private Long roomCreateBy;

    @Column(name = "room_update_at")
    private Instant roomUpdateAt;

    @Column(name = "room_update_by")
    private Long roomUpdateBy;

    public Room() {}

    public Long getId() { return id; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }
    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public boolean isRoomStatus() { return roomStatus; }
    public void setRoomStatus(boolean roomStatus) { this.roomStatus = roomStatus; }
    public Instant getRoomCreateAt() { return roomCreateAt; }
    public void setRoomCreateAt(Instant roomCreateAt) { this.roomCreateAt = roomCreateAt; }
    public Long getRoomCreateBy() { return roomCreateBy; }
    public void setRoomCreateBy(Long roomCreateBy) { this.roomCreateBy = roomCreateBy; }
    public Instant getRoomUpdateAt() { return roomUpdateAt; }
    public void setRoomUpdateAt(Instant roomUpdateAt) { this.roomUpdateAt = roomUpdateAt; }
    public Long getRoomUpdateBy() { return roomUpdateBy; }
    public void setRoomUpdateBy(Long roomUpdateBy) { this.roomUpdateBy = roomUpdateBy; }
}
