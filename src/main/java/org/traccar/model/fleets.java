package org.traccar.model;

import java.util.Date;

public class fleets extends GroupedModel{

    private String id;

    public String getid() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }



    private String workspace_id;

    public String getWorkspace_id() {
        return workspace_id;
    }

    public void setWorkspace_id(String workspace_id) { this.workspace_id = workspace_id; }



    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }



    private String description;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }



    private Date created_at;

    public Date getCreated_at(){return created_at;}

    public void setCreated_at(Date created_at){this.created_at = created_at;}



    private Date deleted_at;

    public Date getDeleted_at(){return deleted_at;}

    public void setDeleted_at(Date deleted_at){this.deleted_at = deleted_at;}



    private String account_id;

    public String getAccount_id(){ return account_id; }

    public void setAccount_id(String account_id){this.account_id = account_id;}

}
