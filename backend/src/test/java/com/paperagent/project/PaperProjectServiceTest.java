package com.paperagent.project;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperProjectServiceTest {

    @Mock
    private PaperProjectRepository repository;

    @Test
    void createShouldTrimUserInputBeforeSaving() {
        PaperProjectService service = new PaperProjectService(repository);
        when(repository.save(org.mockito.ArgumentMatchers.any(PaperProject.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PaperProjectResponse result = service.create(
                new CreatePaperProjectRequest("  毕业论文  ", "  格式修改练习  ")
        );

        ArgumentCaptor<PaperProject> captor = ArgumentCaptor.forClass(PaperProject.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getName()).isEqualTo("毕业论文");
        assertThat(result.description()).isEqualTo("格式修改练习");
    }
}
