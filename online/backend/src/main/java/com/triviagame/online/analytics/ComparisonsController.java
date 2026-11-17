package com.triviagame.online.analytics;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
public class ComparisonsController {
    private final Comparisons comparisons;private final StatisticsImportJobs jobs;
    public ComparisonsController(Comparisons comparisons,StatisticsImportJobs jobs){this.comparisons=comparisons;this.jobs=jobs;}
    @ModelAttribute public void noCache(HttpServletResponse response){response.setHeader("Cache-Control","no-store, private");response.setHeader("Vary","Cookie");}
    private UUID id(Principal user){return UUID.fromString(user.getName());}
    @GetMapping("/comparisons") public List<Comparisons.Member> list(Principal user){return comparisons.list(id(user));}
    @PostMapping("/comparisons/import-jobs") @ResponseStatus(HttpStatus.ACCEPTED)
    public StatisticsImportJobs.Job submit(Principal user,@RequestBody String source,@RequestParam(required=false) String expected){return jobs.submit(id(user),source,expected);}
    @GetMapping("/comparisons/import-jobs") public List<StatisticsImportJobs.Job> recent(Principal user){return jobs.recent(id(user));}
    @GetMapping("/comparisons/import-jobs/{job}") public StatisticsImportJobs.Job job(Principal user,@PathVariable UUID job){return jobs.get(id(user),job);}
    @PostMapping("/comparisons/{member}/request") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(Principal user,@PathVariable UUID member){comparisons.request(id(user),member);}
    @DeleteMapping("/comparisons/{member}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Principal user,@PathVariable UUID member){comparisons.remove(id(user),member);}
    public record Rename(@NotBlank @Size(max=100) String label) { }
    @PatchMapping("/comparisons/{member}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rename(Principal user,@PathVariable UUID member,@Valid @RequestBody Rename input){comparisons.rename(id(user),member,input.label());}
    @GetMapping("/sharing") public Comparisons.Inbox inbox(Principal user){return comparisons.inbox(id(user));}
    @PostMapping("/sharing/{request}/{action:allow|deny|disconnect}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(Principal user,@PathVariable UUID request,@PathVariable String action){comparisons.decide(id(user),request,action);}
}
