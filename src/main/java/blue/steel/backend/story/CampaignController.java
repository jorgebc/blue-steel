package blue.steel.backend.story;

import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

/** Campaign graphql controller. */
@Controller
public class CampaignController {

  private final CampaignService campaignService;

  public CampaignController(CampaignService campaignService) {
    this.campaignService = campaignService;
  }

  @QueryMapping
  public Campaign actualCampaign() {
    return campaignService.findActualCampaign();
  }
}
