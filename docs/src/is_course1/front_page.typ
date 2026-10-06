#import "variables.typ": *

#align(center)[Федеральное государственное автономное образовательное учреждение высшего образования "Национальный исследовательский университет ИТМО"] 

#align(center)[_Факультет программной инженерии и компьютерной техники_ \ _Направление подготовки: 09.03.04 -- Программная инженерия, Системное и прикладное программное обеспечение_] \

#align(center)[Дисциплина: #discipline]

#v(3cm)

#let parts = (
  [*Курсовая работа*],
  [\ *Edu Proj MS*],
  [Информационная система поддержки проектной и учебной деятельности студентов],
  if lab.name != "" and lab.name != none { lab.name } else { [] },
).filter(x => x != [])

#text(size: 16pt)[
  #align(center)[
     #parts.join(linebreak())
  ]
]

#v(6cm)
#align(right)[
  Выполнили: \ #student.name \ Группа: #student.group #parbreak()
  Проверил: \ #teacher
]

#align(bottom + center)[
  г. #footerData.city, #datetime.today().year()
]

#pagebreak()
#counter(page).update(1)