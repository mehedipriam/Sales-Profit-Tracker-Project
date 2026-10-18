package com.salestracker.agent;

import com.salestracker.auth.ApiException;
import com.salestracker.auth.AuthUser;
import com.salestracker.order.OrderDtos.OrderDetail;
import com.salestracker.order.OrderDtos.OrderRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * The order agent. /draft reads a pasted message and prepares an order without saving anything; /confirm saves it once
 * a person has checked it. Anyone who can record an order can use them, with the same limits as the order form.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    public record TextRequest(@NotBlank @Size(max = 4000) String text) {}

    private final AiClient ai;
    private final DraftService drafts;
    private final RevisionService revisions;

    public AgentController(AiClient ai, DraftService drafts, RevisionService revisions) {
        this.ai = ai;
        this.drafts = drafts;
        this.revisions = revisions;
    }

    /** Test endpoint: just what the AI read, before any matching. */
    @PostMapping("/extract")
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public ExtractedOrder extract(@Valid @RequestBody TextRequest req) {
        return ai.extractOrder(req.text());
    }

    @PostMapping("/draft")
    public DraftOrder draft(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TextRequest req) {
        return drafts.prepare(user, req.text());
    }

    /** A screenshot (JPEG, PNG or WebP) instead of pasted text, with an optional note beside it. */
    @PostMapping(value = "/draft/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DraftOrder draftFromImage(@AuthenticationPrincipal AuthUser user,
                                     @RequestPart("image") MultipartFile image,
                                     @RequestParam(value = "text", required = false) String text) {
        if (text != null && text.length() > 4000) throw new ApiException(HttpStatus.BAD_REQUEST, "The note is too long");
        return drafts.prepare(user, text, ImageUpload.read(image));
    }

    /** "Make it 3", "remove the red shirt": changes an open draft as asked and checks it again. Saves nothing. */
    @PostMapping("/revise")
    public RevisionService.Revised revise(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody RevisionService.RevisionRequest req) {
        return revisions.revise(user, req);
    }

    /** The (possibly corrected) draft as an ordinary order request: a customerId, or newCustomer to create one. */
    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetail confirm(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody OrderRequest req) {
        OrderDetail created = drafts.confirm(user, req);
        return user.role().seesFinancials() ? created : created.hideFinancials();
    }
}
