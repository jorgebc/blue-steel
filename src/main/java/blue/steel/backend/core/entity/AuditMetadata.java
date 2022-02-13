package blue.steel.backend.core.entity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;

/** Audit metadata. Audits user and date for creation and last modification. */
@Getter
@Setter
@Embeddable
public class AuditMetadata {

  @ManyToOne
  @JoinColumn(name = "created_by_id")
  @CreatedBy
  private User createdBy;

  @ManyToOne
  @JoinColumn(name = "last_modified_by_id")
  @LastModifiedBy
  private User lastModifiedBy;

  @CreatedDate
  @Column(columnDefinition = "TIMESTAMP")
  @JsonDeserialize(using = LocalDateTimeDeserializer.class)
  @JsonSerialize(using = LocalDateTimeSerializer.class)
  private LocalDateTime creationDate;

  @LastModifiedDate
  @Column(columnDefinition = "TIMESTAMP")
  @JsonDeserialize(using = LocalDateTimeDeserializer.class)
  @JsonSerialize(using = LocalDateTimeSerializer.class)
  private LocalDateTime lastModifiedDate;
}
