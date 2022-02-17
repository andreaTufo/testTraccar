package org.traccar.model;

import java.util.Date;

public class devices extends GroupedModel {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String id;

    public String getid() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    private String fleet_id;

    public String getFleet_id() {
        return fleet_id;
    }

    public void setFleet_id(String fleet_id) {this.fleet_id = fleet_id;}

    private Date created_at;

    public Date getCreated_at(){return created_at;}

    public void setCreated_at(Date created_at){this.created_at = created_at;}

    private Date deleted_at;

    public Date getDeleted_at(){return deleted_at;}

    public void setDeleted_at(Date deleted_at){this.deleted_at = deleted_at;}

}
