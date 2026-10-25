package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.service.dof.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import java.util.*;

@RestController
@RequestMapping("api/v1/admin/dof")
public class DofAdminController {
    @Resource private DofAdminService service;
    @Resource private DofTextService textService;
    @Resource private DofRecordsService recordsService;
    @Resource private DofClientService clientService;
    @Resource private DofSettingsService settingsService;
    @Resource private DofScriptReleaseService scriptReleases;
    @GetMapping("client/releases/status") public Object scriptReleaseStatus(){return scriptReleases.status();}
    @GetMapping("client/releases") public Object scriptReleaseHistory(){return scriptReleases.history();}
    @PostMapping("client/releases/preview") public Object scriptReleasePreview(@RequestBody DofScriptReleaseService.Edit edit)throws java.io.IOException{return scriptReleases.preview(edit);}
    @PostMapping("client/releases") public Object applyScriptRelease(@RequestBody DofScriptReleaseService.Edit edit)throws java.io.IOException{return scriptReleases.apply(edit,null);}
    @PostMapping("client/releases/{id}/restore") public Object restoreScriptRelease(@PathVariable("id") String id,@RequestBody DofScriptReleaseService.Edit edit)throws java.io.IOException{return scriptReleases.apply(edit,id);}
    @PostMapping("client/releases/{id}/reconcile") public Object reconcileScriptRelease(@PathVariable("id") String id){return scriptReleases.reconcile(id);}
    @GetMapping("script") public Object scriptStatus() throws java.io.IOException {return clientService.scriptStatus();}
    @GetMapping("script/uploads") public Object scriptUploads() throws java.io.IOException {return clientService.scriptUploads();}
    @GetMapping("script/uploads/{id}") public Object scriptUpload(@PathVariable("id") String id) throws java.io.IOException {return clientService.scriptUpload(id);}
    @PostMapping(value="script/uploads",consumes="application/octet-stream")
    public Object uploadScript(@RequestParam("requestId") String id,@RequestParam("revision") String revision,
        javax.servlet.http.HttpServletRequest request) throws java.io.IOException {
        // Keep PVF bytes out of multipart buffering and JSON/base64 allocations.
        return clientService.uploadScript(id,revision,request.getContentLengthLong(),request.getInputStream());
    }
    @PostMapping("script/uploads/paired")
    public Object importPairedScript(@RequestBody DofClientService.PairedSourceRequest request)throws java.io.IOException {
        return clientService.importPairedScript(request);
    }
    @GetMapping("script/tasks") public Object scriptTasks()throws java.io.IOException{return clientService.scriptTasks();}
    @PostMapping("script/tasks") public Object createScriptTask(@RequestBody NativeScriptJobs.Request request)throws java.io.IOException{return clientService.submitScript(request);}
    @GetMapping("script/tasks/{id}") public Object scriptTask(@PathVariable("id") String id)throws java.io.IOException{return clientService.scriptTask(id);}
    @GetMapping("script/tasks/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> scriptDownload(@PathVariable("id") String id)throws java.io.IOException {
        java.nio.file.Path path=clientService.downloadScript(id);
        return ResponseEntity.ok().header("Content-Type","application/octet-stream")
            .header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff")
            .header("Content-Disposition","attachment; filename=\"Script.pvf\"")
            .contentLength(java.nio.file.Files.size(path)).body(new org.springframework.core.io.FileSystemResource(path.toFile()));
    }
    @GetMapping("settings/{module}") public Object settings(@PathVariable("module") String module) { return settingsService.read(module); }
    @PostMapping("settings/{module}/preview") public Object settingsPreview(@PathVariable("module") String module, @RequestBody DofSettingsService.Edit edit) { return settingsService.preview(module, edit); }
    @PutMapping("settings/{module}") public Object settingsSave(@PathVariable("module") String module, @RequestBody DofSettingsService.Edit edit) { return settingsService.save(module, edit, null); }
    @GetMapping("settings/{module}/history") public Object settingsHistory(@PathVariable("module") String module) { return settingsService.history(module); }
    @PostMapping("settings/{module}/history/{requestId}/restore") public Object settingsRestore(@PathVariable("module") String module, @PathVariable("requestId") String requestId, @RequestBody DofSettingsService.Edit edit) { return settingsService.save(module, edit, requestId); }
    @PostMapping("settings/{module}/history/{requestId}/reconcile") public Object settingsReconcile(@PathVariable("module") String module, @PathVariable("requestId") String requestId) { return settingsService.reconcile(module, requestId); }
    @GetMapping("client") public Object clientStatus() throws java.io.IOException { return clientService.status(); }
    @GetMapping("client/tasks") public Object clientTasks() throws java.io.IOException { return clientService.list(); }
    @PostMapping("client/tasks") public Object createClientTask(@RequestBody NativeClientJobs.Request request) throws java.io.IOException { return clientService.submit(request); }
    @GetMapping("client/tasks/{id}") public Object clientTask(@PathVariable("id") String id) throws java.io.IOException { return clientService.read(id); }
    @GetMapping("client/tasks/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> clientDownload(@PathVariable("id") String id) throws java.io.IOException {
        java.nio.file.Path path = clientService.download(id);
        return ResponseEntity.ok().header("Content-Type", "application/zip")
            .header("Cache-Control", "private, no-store").header("X-Content-Type-Options", "nosniff")
            .header("Content-Disposition", "attachment; filename=\"dof-isolated-" + id + ".zip\"")
            .contentLength(java.nio.file.Files.size(path)).body(new org.springframework.core.io.FileSystemResource(path.toFile()));
    }
    @GetMapping("records") public Object recordCatalog() { return recordsService.catalog(); }
    @GetMapping("records/{key}") public Object records(@PathVariable("key") String key,
        @RequestParam(value="owner", required=false) Long owner,
        @RequestParam(value="page", defaultValue="1") int page,
        @RequestParam(value="size", defaultValue="50") int size,
        @RequestParam(value="period", defaultValue="all") String period,
        @RequestParam(value="searchField", required=false) String searchField,
        @RequestParam(value="searchText", required=false) String searchText) {
        return recordsService.records(key, owner, page, size,period,searchField,searchText);
    }
    @GetMapping("home") public Object home() { return textService.read(); }
    @PostMapping("home/preview") public Object homePreview(@RequestBody DofTextService.Edit edit) { return textService.preview(edit); }
    @PutMapping("home") public Object homeSave(@RequestBody DofTextService.Edit edit) { return textService.save(edit, null); }
    @GetMapping("home/history") public Object homeHistory() { return textService.history(); }
    @PostMapping("home/history/{requestId}/restore") public Object homeRestore(@PathVariable("requestId") String requestId, @RequestBody DofTextService.Edit edit) { return textService.save(edit, requestId); }
    @PostMapping("home/history/{requestId}/reconcile") public Object homeReconcile(@PathVariable("requestId") String requestId) { return textService.reconcile(requestId); }
    @GetMapping public Object catalog() { return service.catalog(); }
    @GetMapping("ui") public Object ui() { return service.ui(); }
    @GetMapping(value="ui/presets/{id}/image", produces="image/png")
    public ResponseEntity<byte[]> presetImage(@PathVariable("id") String id) {
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
            .header("X-Content-Type-Options", "nosniff").body(service.presetImage(id));
    }
    @PostMapping("ui/preview") public Object preview(@RequestBody DofAdminService.Edit edit) { return service.preview(edit); }
    @PutMapping("ui") public Object save(@RequestBody DofAdminService.Edit edit) { return service.save(edit, null); }
    @GetMapping("ui/history") public Object history() { return service.history(); }
    @PostMapping("ui/history/{requestId}/restore") public Object restore(@PathVariable("requestId") String requestId, @RequestBody DofAdminService.Edit edit) { return service.save(edit, requestId); }
    @PostMapping("ui/history/{requestId}/reconcile") public Object reconcile(@PathVariable("requestId") String requestId) { return service.reconcile(requestId); }
    @ExceptionHandler(DofFailure.class)
    public ResponseEntity<Map<String,String>> failure(DofFailure e) { return ResponseEntity.status(e.status).body(Collections.singletonMap("message", e.getMessage())); }
}
