package org.example.backendv2.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.example.backendv2.entity.enums.ParameterType
import org.hibernate.annotations.UuidGenerator
import org.hibernate.id.uuid.UuidVersion7Strategy
import java.util.UUID

@Entity
@Table(
    name = "task_parameter",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uq_task_parameter_template_name",
            columnNames = ["template_id", "name"],
        ),
    ],
)
class TaskParameter(
    @field:Id
    @field:GeneratedValue
    @field:UuidGenerator(algorithm = UuidVersion7Strategy::class)
    @field:Column(nullable = false, updatable = false)
    var id: UUID? = null,

    @field:NotNull
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "template_id", nullable = false)
    var template: TaskTemplate,

    @field:NotBlank
    @field:Size(max = 100)
    @field:Column(nullable = false, length = 100)
    var name: String,

    @field:Column(columnDefinition = "text")
    var description: String? = null,

    @field:NotNull
    @field:Enumerated(EnumType.STRING)
    @field:Column(name = "data_type", nullable = false, length = 30)
    var dataType: ParameterType,

    @field:Column(name = "default_value", columnDefinition = "text")
    var defaultValue: String? = null,

    @field:Column(nullable = false)
    var required: Boolean = false,
)
