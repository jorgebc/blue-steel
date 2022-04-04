package blue.steel.backend.story.campaign.adapter.dto;

import blue.steel.backend.story.campaign.usecase.dto.CreateCampaignUseCaseInput;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.validation.constraints.NotEmpty;
import lombok.Value;

/** Create campaign input. */
@Value
public class CreateCampaignInput {

  @NotEmpty String name;
  @NotEmpty String description;
  @NotEmpty String imageUrl;

  /**
   * Maps the data to a new creation campaign use case input.
   *
   * @return create campaign use case data
   */
  @JsonIgnore
  public CreateCampaignUseCaseInput getCreateCampaignUseCaseInput() {
    return new CreateCampaignUseCaseInput(name, description, imageUrl);
  }
}
