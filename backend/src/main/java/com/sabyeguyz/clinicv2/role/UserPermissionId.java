package com.sabyeguyz.clinicv2.role;

import java.io.Serializable;
import java.util.Objects;

<<<<<<< HEAD
=======
/** คีย์ร่วมของรายการ override: ผู้ใช้และ permission */
>>>>>>> 3c7da8a915aea6793ee49626494196b33cc60e17
public class UserPermissionId implements Serializable {
    private Long user;
    private Long permission;

    public UserPermissionId() {}

    public UserPermissionId(Long user, Long permission) {
        this.user = user;
        this.permission = permission;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UserPermissionId that)) return false;
<<<<<<< HEAD
        return Objects.equals(user, that.user)
                && Objects.equals(permission, that.permission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, permission);
    }
}
=======
        return Objects.equals(user, that.user) && Objects.equals(permission, that.permission);
    }

    @Override
    public int hashCode() { return Objects.hash(user, permission); }
}
>>>>>>> 3c7da8a915aea6793ee49626494196b33cc60e17
