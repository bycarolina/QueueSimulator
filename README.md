# QueueSimulator
Simulador de rede de filas em Java para disciplina de Simulação e Métodos Analíticos. Implementa duas filas em tandem, iniciando vazias, com primeira chegada em t=1.5 e encerrando ao consumir o 100.000º aleatório.

## Especificações:
- **Fila 1 (F1)**: G/G/2/3, chegadas uniformes entre 1 e 4, atendimento entre 3 e 4.
- **Fila 2 (F2)**: G/G/1/5, sem chegadas externas, atendimento entre 2 e 3.
- A Fila 2 recebe 100% dos clientes da Fila 1.
- As filas iniciam vazias e a primeira chegada ocorre em t = 1.5
- A simulação encerra no 100.000º número aleatório gerado.
