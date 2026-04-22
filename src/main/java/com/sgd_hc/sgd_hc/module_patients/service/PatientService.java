package com.sgd_hc.sgd_hc.module_patients.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sgd_hc.sgd_hc.module_patients.dto.PatientCreateDto;
import com.sgd_hc.sgd_hc.module_patients.dto.PatientResponseDto;
import com.sgd_hc.sgd_hc.module_patients.dto.PatientUpdateDto;
import com.sgd_hc.sgd_hc.module_patients.entity.Patient;
import com.sgd_hc.sgd_hc.module_patients.mapper.PatientMapper;
import com.sgd_hc.sgd_hc.module_patients.repository.PatientRepository;
import com.sgd_hc.sgd_hc.module_users.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository    userRepository;
    private final PatientMapper     patientMapper;
    private final PasswordEncoder   passwordEncoder;

    @Transactional
    public PatientResponseDto createPatient(PatientCreateDto dto) {
        if (userRepository.existsByEmail(dto.email()))
            throw new IllegalArgumentException("Email already exists");

        Patient patient = patientMapper.toEntity(dto);
        patient.setUsername(generateUsername());
        patient.setPassword(passwordEncoder.encode(dto.password()));

        return patientMapper.toResponseDto(patientRepository.save(patient));
    }

    @Transactional(readOnly = true)
    public List<PatientResponseDto> getAllPatients() {
        return patientRepository.findAll().stream()
                .map(patientMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PatientResponseDto getPatientById(Long id) {
        return patientMapper.toResponseDto(findOrThrow(id));
    }

    @Transactional
    public PatientResponseDto updatePatient(Long id, PatientUpdateDto dto) {
        Patient patient = findOrThrow(id);
        patientMapper.updateEntityFromDto(dto, patient);
        if (dto.password() != null && !dto.password().isBlank())
            patient.setPassword(passwordEncoder.encode(dto.password()));
        return patientMapper.toResponseDto(patientRepository.save(patient));
    }

    @Transactional
    public void deletePatient(Long id) {
        Patient patient = findOrThrow(id);
        patient.setIsActive(false);
        patientRepository.save(patient);
    }

    // ─── helpers ────────────────────────────────────────────────────────────────

    private Patient findOrThrow(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Patient not found with id: " + id));
    }

    private String generateUsername() {
        String prefix = "PAT";
        String code;
        do {
            int n = (int) (Math.random() * 9000) + 1000;
            code = prefix + "-" + n;
        } while (userRepository.existsByUsername(code));
        return code;
    }
}
