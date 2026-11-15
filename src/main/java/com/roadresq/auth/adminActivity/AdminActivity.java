package com.roadresq.auth.adminActivity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document("admin_activity")
@CompoundIndex(name = "platform_activity_date", def = "{'servicePlatformType':1,'source':1,'createdAt':-1}")
public class AdminActivity {
    @Id private String id;
    private String source;
    private String actorId;
    private String action;
    private String targetId;
    private String reason;
    private String beforeState;
    private String afterState;
    private String servicePlatformType;
    private LocalDateTime createdAt;
}